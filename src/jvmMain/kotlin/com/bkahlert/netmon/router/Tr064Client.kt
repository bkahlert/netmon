package com.bkahlert.netmon.router

import com.bkahlert.netmon.xml.SecureXml
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.xml.stream.XMLStreamConstants

class Tr064Exception(message: String) : RuntimeException(message)

/**
 * SOAP calls to a FRITZ!Box's TR-064 `Hosts` service.
 *
 * An action the box answers with 401 is repeated once with Digest [credentials]; the body goes with both requests,
 * since the box answers an empty body with `XML error` instead of a challenge.
 *
 * Reading a response is bounded: a SOAP answer is at most 256 KB and a [get] stream at most 1 MB, and reading either
 * must finish within [soapTimeout] respectively [getTimeout] of the response headers, else an exception is thrown.
 */
class Tr064Client(
    private val base: URI,
    private val credentials: Credentials?,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val soapTimeout: Duration = Duration.ofSeconds(10),
    private val getTimeout: Duration = Duration.ofSeconds(20),
) {

    /** Returns the response arguments of [action], without their `New` prefix. */
    fun hosts(action: String, arguments: Map<String, String> = emptyMap()): Map<String, String> {
        val body = envelope(action, arguments)
        val request = HttpRequest.newBuilder(base.resolve(CONTROL_PATH))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .header("SoapAction", "\"$SERVICE#$action\"")
        var response = post(request, body, action)
        if (response.status == 401 && credentials != null) {
            val challenge = response.challenge ?: throw Tr064Exception("401 without a challenge for $action")
            response = post(request.header("Authorization", DigestAuth.authorization(challenge, "POST", CONTROL_PATH, credentials)), body, action)
        }
        if (response.status != 200) throw Tr064Exception("$action failed with ${response.status}: ${fault(response.body)}")
        return arguments(response.body)
    }

    private class Reply(val status: Int, val challenge: String?, val body: String)

    private fun post(request: HttpRequest.Builder, body: String, action: String): Reply {
        val response = http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofInputStream())
        val text = try {
            BoundedInputStream(response.body(), MAX_SOAP_BYTES, soapTimeout).use { it.readAllBytes().decodeToString() }
        } catch (e: IOException) {
            throw Tr064Exception("$action response unreadable: ${e.message}")
        }
        return Reply(response.statusCode(), response.headers().firstValue("WWW-Authenticate").orElse(null), text)
    }

    /** Opens [path] relative to the base, for example the host list a `X_AVM-DE_GetHostListPath` call returned; reading it throws an [IOException] past 1 MB or the [getTimeout]. */
    fun get(path: String): InputStream {
        val response = http.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw Tr064Exception("GET ${path.substringBefore('?')} failed with ${response.statusCode()}")
        }
        return BoundedInputStream(response.body(), MAX_GET_BYTES, getTimeout)
    }

    private fun envelope(action: String, arguments: Map<String, String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
        append("<u:$action xmlns:u=\"$SERVICE\">")
        arguments.forEach { (name, value) -> append("<New$name>").append(value.escape()).append("</New$name>") }
        append("</u:$action></s:Body></s:Envelope>")
    }

    private fun String.escape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun arguments(xml: String): Map<String, String> = buildMap {
        val reader = SecureXml.reader(xml)
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName.startsWith("New")) {
                    put(reader.localName.removePrefix("New"), reader.elementText.trim())
                }
            }
        } finally {
            reader.close()
        }
    }

    private fun fault(xml: String): String = Regex("<errorDescription>(?<text>[^<]*)</errorDescription>").find(xml)?.groups?.get("text")?.value ?: xml.take(200)

    companion object {
        const val SERVICE = "urn:dslforum-org:service:Hosts:1"
        const val CONTROL_PATH = "/upnp/control/hosts"
        private const val MAX_SOAP_BYTES = 256L * 1024
        private const val MAX_GET_BYTES = 1024L * 1024

        private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "tr064-deadline").apply { isDaemon = true }
        }
    }

    /** Passes at most [limit] bytes and closes [delegate] after [timeout], which also ends a blocked read; both surface as an [IOException]. */
    private class BoundedInputStream(delegate: InputStream, private val limit: Long, private val timeout: Duration) : FilterInputStream(delegate) {

        private var count = 0L

        @Volatile
        private var expired = false
        private val deadline: ScheduledFuture<*> = timer.schedule({
            expired = true
            runCatching { delegate.close() }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS)

        override fun read(): Int {
            val buffer = ByteArray(1)
            val n = read(buffer, 0, 1)
            return if (n < 0) -1 else buffer[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val n = try {
                super.read(b, off, minOf(len.toLong(), limit - count + 1).toInt())
            } catch (e: IOException) {
                if (expired) throw IOException("not finished within $timeout", e)
                throw e
            }
            if (expired) throw IOException("not finished within $timeout")
            if (n > 0) count += n
            if (count > limit) throw IOException("longer than $limit bytes")
            return n
        }

        override fun skip(n: Long): Long = 0

        override fun close() {
            deadline.cancel(false)
            super.close()
        }
    }
}
