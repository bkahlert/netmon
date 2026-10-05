package com.bkahlert.netmon.router

import com.bkahlert.netmon.xml.SecureXml
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.stream.XMLStreamConstants

class Tr064Exception(message: String) : RuntimeException(message)

/**
 * SOAP calls to a FRITZ!Box's TR-064 `Hosts` service.
 *
 * An action the box answers with 401 is repeated once with Digest [credentials]; the body goes with both requests,
 * since the box answers an empty body with `XML error` instead of a challenge.
 */
class Tr064Client(
    private val base: URI,
    private val credentials: Credentials?,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) {

    /** Returns the response arguments of [action], without their `New` prefix. */
    fun hosts(action: String, arguments: Map<String, String> = emptyMap()): Map<String, String> {
        val body = envelope(action, arguments)
        val request = HttpRequest.newBuilder(base.resolve(CONTROL_PATH))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "text/xml; charset=\"utf-8\"")
            .header("SoapAction", "\"$SERVICE#$action\"")
        var response = http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 401 && credentials != null) {
            val challenge = response.headers().firstValue("WWW-Authenticate").orElseThrow { Tr064Exception("401 without a challenge for $action") }
            response = http.send(
                request.header("Authorization", DigestAuth.authorization(challenge, "POST", CONTROL_PATH, credentials))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }
        if (response.statusCode() != 200) throw Tr064Exception("$action failed with ${response.statusCode()}: ${fault(response.body())}")
        return arguments(response.body())
    }

    /** Opens [path] relative to the base, for example the host list a `X_AVM-DE_GetHostListPath` call returned. */
    fun get(path: String): InputStream {
        val response = http.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw Tr064Exception("GET ${path.substringBefore('?')} failed with ${response.statusCode()}")
        }
        return response.body()
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
    }
}
