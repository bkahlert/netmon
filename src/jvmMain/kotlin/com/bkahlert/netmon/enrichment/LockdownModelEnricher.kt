package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.xml.SecureXml
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.xml.stream.XMLStreamConstants
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * An enricher that reads the model of an Apple mobile device from iOS `lockdownd` on TCP port [LOCKDOWN_PORT].
 *
 * The device answers `GetValue` for `ProductType` (for example `iPad7,5`) without pairing. The protocol is undocumented.
 * Only a [Host] without a model whose vendor is unknown or Apple is probed. An answer is remembered for a day, a failure
 * for five minutes, per MAC address (per IP without one).
 */
class LockdownModelEnricher(
    private val port: Int = LOCKDOWN_PORT,
    private val clock: Clock = Clock.System,
    private val connectTimeout: Duration = 1.seconds,
    private val readTimeout: Duration = 2.seconds,
) : HostEnricher {

    private val logger by SLF4J

    private class Probed(val model: String?, val expiresAt: Instant)

    private val cache = ConcurrentHashMap<String, Probed>()

    override fun enrich(entity: Host): Host? {
        if (entity.model != null || !(entity.vendor == null || entity.vendor.startsWith("Apple", ignoreCase = true))) return null
        val now = clock.now()
        val key = entity.mac ?: entity.ip.toString()
        val probed = cache[key]?.takeIf { it.expiresAt > now } ?: run {
            val model = probe(entity.ip.bytes)
            Probed(model, now + if (model != null) SUCCESS_TTL else FAILURE_TTL).also { cache[key] = it }
        }
        val model = probed.model ?: return null
        return entity.copy(model = model, vendor = entity.vendor ?: "Apple Inc.")
            .also { logger.info("{} enriched: model={}", it, model) }
    }

    private fun probe(address: ByteArray): String? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByAddress(address), port), connectTimeout.inWholeMilliseconds.toInt())
            val deadline = System.nanoTime() + readTimeout.inWholeNanoseconds
            val request = REQUEST.toByteArray()
            DataOutputStream(socket.getOutputStream()).apply {
                writeInt(request.size)
                write(request)
                flush()
            }
            val length = ByteBuffer.wrap(socket.readFully(4, deadline)).getInt()
            require(length in 1..MAX_REPLY_BYTES) { "Reply length $length" }
            valueOf(String(socket.readFully(length, deadline)))
        }
    } catch (e: InterruptedException) {
        throw e
    } catch (e: Exception) {
        logger.debug("No model from {}:{}: {}", InetAddress.getByAddress(address).hostAddress, port, e.toString())
        null
    }

    /** Reads [length] bytes, giving up at [deadline] (a [System.nanoTime] value) however slowly they arrive. */
    private fun Socket.readFully(length: Int, deadline: Long): ByteArray {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val remainingMillis = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMillis <= 0) throw SocketTimeoutException("Reply not complete within $readTimeout")
            soTimeout = remainingMillis.toInt()
            val count = getInputStream().read(bytes, read, length - read)
            if (count < 0) throw EOFException("Reply ended after $read of $length bytes")
            read += count
        }
        return bytes
    }

    private fun valueOf(plist: String): String? {
        val reader = SecureXml.reader(plist)
        try {
            var afterValueKey = false
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    when (reader.localName) {
                        "key" -> afterValueKey = reader.elementText == "Value"
                        "string" -> if (afterValueKey) return reader.elementText.takeIf { it.isNotBlank() }
                        else -> afterValueKey = false
                    }
                }
            }
            return null
        } finally {
            reader.close()
        }
    }

    override fun toString(): String = this::class.simpleName ?: "<object>"

    companion object {
        const val LOCKDOWN_PORT = 62078
        private const val MAX_REPLY_BYTES = 64 * 1024
        private val SUCCESS_TTL = 24.hours
        private val FAILURE_TTL = 5.minutes
        private const val REQUEST = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>Request</key><string>GetValue</string><key>Key</key><string>ProductType</string><key>Label</key><string>netmon</string></dict></plist>"""
    }
}
