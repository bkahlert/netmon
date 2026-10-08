package com.bkahlert.netmon.scanner.discovery.router

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test

class Tr064ClientBoundsTest {

    private class Server : AutoCloseable {
        private val release = CountDownLatch(1)
        private val executor = Executors.newCachedThreadPool()
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@Server.executor
            createContext("/upnp/control/hosts") { exchange ->
                if (exchange.requestHeaders.getFirst("SoapAction").orEmpty().endsWith("#Stall\"")) exchange.stall() else exchange.send(300 * 1024)
            }
            createContext("/big") { exchange -> exchange.send(2 * 1024 * 1024) }
            createContext("/stall") { exchange -> exchange.stall() }
            start()
        }
        val base: URI get() = URI("http://127.0.0.1:${server.address.port}")

        private fun HttpExchange.send(size: Int) {
            sendResponseHeaders(200, size.toLong())
            responseBody.use { out -> out.write(ByteArray(size) { 'a'.code.toByte() }) }
        }

        private fun HttpExchange.stall() {
            sendResponseHeaders(200, 1000)
            responseBody.write("<x>".toByteArray())
            responseBody.flush()
            release.await()
        }

        override fun close() {
            release.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private val quick = Duration.ofMillis(300)

    @Test
    fun a_soap_response_over_256_kb_is_rejected() {
        Server().use { server ->
            val error = shouldThrow<Tr064Exception> { Tr064Client(server.base, credentials = null).hosts("GetHostNumberOfEntries") }

            error.message shouldContain "longer than 262144 bytes"
        }
    }

    @Test
    fun a_get_stream_over_1_mb_fails_instead_of_truncating() {
        Server().use { server ->
            val error = shouldThrow<IOException> { Tr064Client(server.base, credentials = null).get("/big").use { it.readBytes() } }

            error.message shouldContain "longer than 1048576 bytes"
        }
    }

    @Test
    fun a_stalled_soap_body_times_out() {
        Server().use { server ->
            val start = System.nanoTime()

            val error = shouldThrow<Tr064Exception> { Tr064Client(server.base, credentials = null, soapTimeout = quick).hosts("Stall") }

            error.message shouldContain "not finished within"
            ((System.nanoTime() - start) / 1_000_000) shouldBeLessThan 5_000
        }
    }

    @Test
    fun a_stalled_get_body_times_out() {
        Server().use { server ->
            val start = System.nanoTime()

            val error = shouldThrow<IOException> { Tr064Client(server.base, credentials = null, getTimeout = quick).get("/stall").use { it.readBytes() } }

            error.message shouldContain "not finished within"
            ((System.nanoTime() - start) / 1_000_000) shouldBeLessThan 5_000
        }
    }
}
