package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.epoch
import com.bkahlert.netmon.invoke
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LockdownModelEnricherTest {

    @Test
    fun a_host_without_vendor_gets_the_model_and_apple_as_vendor() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate())

            result shouldBe candidate().copy(model = "iPad7,5", vendor = "Apple Inc.")
        }
    }

    @Test
    fun a_host_with_an_apple_vendor_keeps_it() {
        FakeLockdownd(reply = plist("iPad8,3")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate(vendor = "Apple"))

            result shouldBe candidate(vendor = "Apple").copy(model = "iPad8,3")
        }
    }

    @Test
    fun the_request_asks_for_the_product_type() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            LockdownModelEnricher(port = server.port).enrich(candidate())

            server.requests.single() should {
                it shouldContain "<key>Request</key><string>GetValue</string>"
                it shouldContain "<key>Key</key><string>ProductType</string>"
            }
        }
    }

    @Test
    fun a_known_model_is_not_asked_again_within_a_day() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, clock = TestClock())

            val first = enricher.enrich(candidate())
            val second = enricher.enrich(candidate())

            first?.model shouldBe "iPad7,5"
            second?.model shouldBe "iPad7,5"
            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun a_known_model_is_asked_again_after_a_day() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val clock = TestClock()
            val enricher = LockdownModelEnricher(port = server.port, clock = clock)
            enricher.enrich(candidate())

            clock.advance(24.hours + 1.seconds)
            enricher.enrich(candidate())

            server.requests shouldHaveSize 2
        }
    }

    @Test
    fun a_device_that_moved_is_found_in_the_cache_by_its_mac() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, clock = TestClock())
            enricher.enrich(candidate(ip = "127.0.0.1"))

            val result = enricher.enrich(candidate(ip = "127.0.0.2"))

            result?.model shouldBe "iPad7,5"
            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun without_a_mac_the_ip_is_the_cache_key() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, clock = TestClock())

            enricher.enrich(candidate(mac = null))
            enricher.enrich(candidate(mac = null))

            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun a_failed_probe_is_retried_after_five_minutes() {
        FakeLockdownd(reply = errorReply("GetProhibited")).use { server ->
            val clock = TestClock()
            val enricher = LockdownModelEnricher(port = server.port, clock = clock)

            val first = enricher.enrich(candidate())
            clock.advance(4.minutes)
            enricher.enrich(candidate())
            val requestsBeforeRetry = server.requests.size
            clock.advance(1.minutes + 1.seconds)
            enricher.enrich(candidate())

            first.shouldBeNull()
            requestsBeforeRetry shouldBe 1
            server.requests shouldHaveSize 2
        }
    }

    @Test
    fun a_host_with_another_vendor_is_not_probed() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate(vendor = "Raspberry Pi Trading"))

            result.shouldBeNull()
            server.requests.shouldBeEmpty()
        }
    }

    @Test
    fun a_host_with_a_model_is_not_probed() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate(model = "iPad8,3"))

            result.shouldBeNull()
            server.requests.shouldBeEmpty()
        }
    }

    @Test
    fun a_closed_port_yields_nothing() {
        val closedPort = ServerSocket(0).use { it.localPort }

        LockdownModelEnricher(port = closedPort).enrich(candidate()).shouldBeNull()
    }

    @Test
    fun a_device_that_never_answers_yields_nothing_after_the_read_timeout() {
        FakeLockdownd(reply = null).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, readTimeout = 200.milliseconds)

            enricher.enrich(candidate()).shouldBeNull()
        }
    }

    @Test
    fun an_absurd_reply_length_yields_nothing() {
        FakeLockdownd(reply = ByteBuffer.allocate(4).putInt(Int.MAX_VALUE).array()).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, readTimeout = 200.milliseconds)

            enricher.enrich(candidate()).shouldBeNull()
        }
    }

    @Test
    fun a_reply_that_is_not_a_plist_yields_nothing() {
        FakeLockdownd(reply = framed("not xml at all")).use { server ->
            LockdownModelEnricher(port = server.port).enrich(candidate()).shouldBeNull()
        }
    }

    @Test
    fun an_external_dtd_is_not_fetched() {
        val body = """<?xml version="1.0"?><!DOCTYPE plist SYSTEM "file:///nonexistent/PropertyList.dtd">
            |<plist version="1.0"><dict><key>Value</key><string>iPad7,5</string></dict></plist>""".trimMargin()
        FakeLockdownd(reply = framed(body)).use { server ->
            LockdownModelEnricher(port = server.port).enrich(candidate())?.model shouldBe "iPad7,5"
        }
    }
}

private fun candidate(
    ip: String = "127.0.0.1",
    mac: String? = "aa:bb:cc:dd:ee:01",
    vendor: String? = null,
    model: String? = null,
) = Host(ip = ip, name = null, model = model, vendor = vendor, services = null, mac = mac)

private fun framed(body: String): ByteArray = body.toByteArray().let { ByteBuffer.allocate(4 + it.size).putInt(it.size).put(it).array() }

private fun plist(value: String): ByteArray = framed(
    """<?xml version="1.0" encoding="UTF-8"?>
    |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    |<plist version="1.0"><dict><key>Key</key><string>ProductType</string><key>Request</key><string>GetValue</string><key>Value</key><string>$value</string></dict></plist>""".trimMargin(),
)

private fun errorReply(error: String): ByteArray = framed(
    """<?xml version="1.0" encoding="UTF-8"?>
    |<plist version="1.0"><dict><key>Error</key><string>$error</string><key>Request</key><string>GetValue</string></dict></plist>""".trimMargin(),
)

private class TestClock(private var current: Instant = 0.epoch) : Clock {
    override fun now(): Instant = current
    fun advance(by: Duration) {
        current += by
    }
}

private class FakeLockdownd(private val reply: ByteArray?) : AutoCloseable {

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val sockets = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<String>()
    val port: Int get() = server.localPort

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    return@thread
                }
                sockets += socket
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket) {
        try {
            val input = DataInputStream(socket.getInputStream())
            requests += String(ByteArray(input.readInt()).also(input::readFully))
            reply?.let { socket.getOutputStream().apply { write(it); flush() } }
        } catch (e: IOException) {
            // the client left
        }
    }

    override fun close() {
        server.close()
        sockets.forEach { it.close() }
    }
}
