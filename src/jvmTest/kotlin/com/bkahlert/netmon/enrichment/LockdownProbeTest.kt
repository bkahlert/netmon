package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.epoch
import com.bkahlert.netmon.invoke
import io.kotest.matchers.comparables.shouldBeLessThan
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
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LockdownProbeTest {

    @Test
    fun a_reachable_device_answers_its_product_type() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val result = LockdownProbe(port = server.port).model(candidate().ip, candidate().mac)

            result shouldBe "iPad7,5"
        }
    }

    @Test
    fun the_request_asks_for_the_product_type() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            LockdownProbe(port = server.port).model(candidate().ip, candidate().mac)

            server.requests.single() should {
                it shouldContain "<key>Request</key><string>GetValue</string>"
                it shouldContain "<key>Key</key><string>ProductType</string>"
            }
        }
    }

    @Test
    fun a_known_model_is_not_asked_again_within_a_day() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val probe = LockdownProbe(port = server.port, clock = TestClock())

            val first = probe.model(candidate().ip, candidate().mac)
            val second = probe.model(candidate().ip, candidate().mac)

            first shouldBe "iPad7,5"
            second shouldBe "iPad7,5"
            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun a_known_model_is_asked_again_after_a_day() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val clock = TestClock()
            val probe = LockdownProbe(port = server.port, clock = clock)
            probe.model(candidate().ip, candidate().mac)

            clock.advance(24.hours + 1.seconds)
            probe.model(candidate().ip, candidate().mac)

            server.requests shouldHaveSize 2
        }
    }

    @Test
    fun a_device_that_moved_is_found_in_the_cache_by_its_mac() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val probe = LockdownProbe(port = server.port, clock = TestClock())
            probe.model(candidate(ip = "127.0.0.1").ip, candidate(ip = "127.0.0.1").mac)

            val result = probe.model(candidate(ip = "127.0.0.2").ip, candidate(ip = "127.0.0.2").mac)

            result shouldBe "iPad7,5"
            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun without_a_mac_the_ip_is_the_cache_key() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val probe = LockdownProbe(port = server.port, clock = TestClock())

            probe.model(candidate(mac = null).ip, candidate(mac = null).mac)
            probe.model(candidate(mac = null).ip, candidate(mac = null).mac)

            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun a_failed_probe_is_retried_after_five_minutes() {
        FakeLockdownd(reply = errorReply("GetProhibited")).use { server ->
            val clock = TestClock()
            val probe = LockdownProbe(port = server.port, clock = clock)

            val first = probe.model(candidate().ip, candidate().mac)
            clock.advance(4.minutes)
            probe.model(candidate().ip, candidate().mac)
            val requestsBeforeRetry = server.requests.size
            clock.advance(1.minutes + 1.seconds)
            probe.model(candidate().ip, candidate().mac)

            first.shouldBeNull()
            requestsBeforeRetry shouldBe 1
            server.requests shouldHaveSize 2
        }
    }

    @Test
    fun a_closed_port_yields_nothing() {
        val closedPort = ServerSocket(0).use { it.localPort }

        LockdownProbe(port = closedPort).model(candidate().ip, candidate().mac).shouldBeNull()
    }

    @Test
    fun a_device_that_never_answers_yields_nothing_after_the_read_timeout() {
        FakeLockdownd(reply = null).use { server ->
            val probe = LockdownProbe(port = server.port, readTimeout = 200.milliseconds)

            probe.model(candidate().ip, candidate().mac).shouldBeNull()
        }
    }

    @Test
    fun a_device_that_trickles_its_reply_is_given_up_on_after_the_read_timeout() {
        val slowReply = ByteBuffer.allocate(104).putInt(60_000).put(ByteArray(100)).array()
        FakeLockdownd(reply = slowReply, byteDelay = 100.milliseconds).use { server ->
            val probe = LockdownProbe(port = server.port, readTimeout = 300.milliseconds)

            val started = System.nanoTime()
            val result = probe.model(candidate().ip, candidate().mac)
            val elapsed = (System.nanoTime() - started).nanoseconds

            result.shouldBeNull()
            elapsed shouldBeLessThan 3.seconds
        }
    }

    @Test
    fun an_absurd_reply_length_yields_nothing() {
        FakeLockdownd(reply = ByteBuffer.allocate(4).putInt(Int.MAX_VALUE).array()).use { server ->
            val probe = LockdownProbe(port = server.port, readTimeout = 200.milliseconds)

            probe.model(candidate().ip, candidate().mac).shouldBeNull()
        }
    }

    @Test
    fun a_reply_that_is_not_a_plist_yields_nothing() {
        FakeLockdownd(reply = framed("not xml at all")).use { server ->
            LockdownProbe(port = server.port).model(candidate().ip, candidate().mac).shouldBeNull()
        }
    }

    @Test
    fun an_external_dtd_is_not_fetched() {
        val body = """<?xml version="1.0"?><!DOCTYPE plist SYSTEM "file:///nonexistent/PropertyList.dtd">
            |<plist version="1.0"><dict><key>Value</key><string>iPad7,5</string></dict></plist>""".trimMargin()
        FakeLockdownd(reply = framed(body)).use { server ->
            LockdownProbe(port = server.port).let { it.model(candidate().ip, candidate().mac) } shouldBe "iPad7,5"
        }
    }
}

private fun candidate(
    ip: String = "127.0.0.1",
    mac: String? = "aa:bb:cc:dd:ee:01",
) = Host(ip = ip, name = null, model = null, vendor = null, services = null, mac = mac)

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

private class FakeLockdownd(private val reply: ByteArray?, private val byteDelay: Duration = Duration.ZERO) : AutoCloseable {

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
            reply?.let { bytes ->
                val output = socket.getOutputStream()
                if (byteDelay == Duration.ZERO) {
                    output.write(bytes)
                    output.flush()
                } else {
                    bytes.forEach {
                        output.write(it.toInt())
                        output.flush()
                        Thread.sleep(byteDelay.inWholeMilliseconds)
                    }
                }
            }
        } catch (e: IOException) {
            // the client left
        }
    }

    override fun close() {
        server.close()
        sockets.forEach { it.close() }
    }
}
