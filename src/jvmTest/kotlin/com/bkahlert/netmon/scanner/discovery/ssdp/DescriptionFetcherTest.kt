package com.bkahlert.netmon.scanner.discovery.ssdp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class DescriptionFetcherTest {

    @Test
    fun reads_the_description_a_location_serves() {
        Server().use { server ->
            val result = DescriptionFetcher()(server.uri("/description.xml"))

            result shouldBe DeviceDescription("Test Bridge", "Example Corp", "Test Bridge", "TB001", "urn:schemas-upnp-org:device:Basic:1", "uuid:00000000-0000-4000-8000-000000000001")
        }
    }

    @Test
    fun asks_with_http_1_1_and_no_h2c_upgrade() {
        Server().use { server ->
            DescriptionFetcher()(server.uri("/description.xml"))

            server.upgrades shouldBe listOf(null)
        }
    }

    @Test
    fun a_location_answering_other_than_200_yields_nothing() {
        Server().use { server ->
            val result = DescriptionFetcher()(server.uri("/unprocessable"))

            result.shouldBeNull()
        }
    }

    @Test
    fun a_description_over_256_kb_fails_instead_of_truncating() {
        Server().use { server ->
            val error = shouldThrow<IOException> { DescriptionFetcher()(server.uri("/big")) }

            error.message shouldContain "longer than 262144 bytes"
        }
    }

    @Test
    fun a_body_served_slowly_times_out() {
        Server().use { server ->
            val start = System.nanoTime()

            val error = shouldThrow<IOException> { DescriptionFetcher(timeout = 300.milliseconds)(server.uri("/stall-body")) }

            error.message shouldContain "not finished within"
            ((System.nanoTime() - start) / 1_000_000) shouldBeLessThan 5_000
        }
    }

    @Test
    fun a_location_that_sends_no_headers_times_out() {
        Server().use { server ->
            val start = System.nanoTime()

            shouldThrow<IOException> { DescriptionFetcher(timeout = 300.milliseconds)(server.uri("/stall-headers")) }

            ((System.nanoTime() - start) / 1_000_000) shouldBeLessThan 5_000
        }
    }

    private class Server : AutoCloseable {
        private val release = CountDownLatch(1)
        private val executor = Executors.newCachedThreadPool()
        val upgrades: MutableList<String?> = java.util.Collections.synchronizedList(mutableListOf())
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@Server.executor
            createContext("/description.xml") { exchange ->
                upgrades += exchange.requestHeaders.getFirst("Upgrade")
                exchange.send(200, DESCRIPTION.toByteArray())
            }
            createContext("/unprocessable") { exchange -> exchange.send(422, "<html><body>422</body></html>".toByteArray()) }
            createContext("/big") { exchange -> exchange.send(200, ByteArray(300 * 1024) { 'a'.code.toByte() }) }
            createContext("/stall-body") { exchange ->
                exchange.sendResponseHeaders(200, 1000)
                exchange.responseBody.write("<root>".toByteArray())
                exchange.responseBody.flush()
                release.await()
            }
            createContext("/stall-headers") { release.await() }
            start()
        }

        fun uri(path: String): URI = URI("http://127.0.0.1:${server.address.port}$path")

        private fun HttpExchange.send(status: Int, body: ByteArray) {
            sendResponseHeaders(status, body.size.toLong())
            responseBody.use { it.write(body) }
        }

        override fun close() {
            release.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}

private const val DESCRIPTION = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device><deviceType>urn:schemas-upnp-org:device:Basic:1</deviceType><friendlyName>Test Bridge</friendlyName><manufacturer>Example Corp</manufacturer><modelName>Test Bridge</modelName><modelNumber>TB001</modelNumber><UDN>uuid:00000000-0000-4000-8000-000000000001</UDN></device></root>"""
