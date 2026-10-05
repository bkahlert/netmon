package com.bkahlert.netmon.ssdp

import com.bkahlert.netmon.IP
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SsdpCacheTest {

    @Test
    fun an_announcement_with_a_new_location_is_fetched_and_kept_by_sender_ip() {
        val fetched = mutableListOf<URI>()
        val cache = SsdpCache(fetch = { fetched += it; BRIDGE })

        cache.offer(bridge(), IP.of("192.0.2.5"))

        cache.device(IP.of("192.0.2.5")) shouldBe BRIDGE
        fetched shouldBe listOf(URI("http://192.0.2.5:80/description.xml"))
    }

    @Test
    fun a_known_location_is_not_fetched_again_while_fresh() {
        var fetches = 0
        val cache = SsdpCache(fetch = { fetches++; BRIDGE }, clock = TestClock())

        cache.offer(bridge(), IP.of("192.0.2.5"))
        cache.offer(bridge(), IP.of("192.0.2.5"))

        fetches shouldBe 1
    }

    @Test
    fun an_entry_expires_when_no_announcement_renews_it() {
        val clock = TestClock()
        val cache = SsdpCache(fetch = { BRIDGE }, clock = clock, ttl = 30.minutes)
        cache.offer(bridge(), IP.of("192.0.2.5"))

        clock.advance(31.minutes)

        cache.device(IP.of("192.0.2.5")).shouldBeNull()
    }

    @Test
    fun a_renewed_announcement_keeps_the_entry_alive_without_a_fetch() {
        var fetches = 0
        val clock = TestClock()
        val cache = SsdpCache(fetch = { fetches++; BRIDGE }, clock = clock, ttl = 30.minutes)
        cache.offer(bridge(), IP.of("192.0.2.5"))

        clock.advance(20.minutes)
        cache.offer(bridge(), IP.of("192.0.2.5"))
        clock.advance(20.minutes)

        cache.device(IP.of("192.0.2.5")) shouldBe BRIDGE
        fetches shouldBe 1
    }

    @Test
    fun a_failing_location_is_not_fetched_again_for_the_ttl() {
        var fetches = 0
        val clock = TestClock()
        val cache = SsdpCache(fetch = { fetches++; null }, clock = clock)

        cache.offer(bridge(), IP.of("192.0.2.19"))
        cache.offer(bridge(), IP.of("192.0.2.19"))
        clock.advance(31.minutes)
        cache.offer(bridge(), IP.of("192.0.2.19"))

        fetches shouldBe 2
        cache.device(IP.of("192.0.2.19")).shouldBeNull()
    }

    @Test
    fun a_fetch_that_throws_is_remembered_as_failed() {
        var fetches = 0
        val cache = SsdpCache(fetch = { fetches++; throw IOException("not finished within PT3S") }, clock = TestClock())

        cache.offer(bridge(), IP.of("192.0.2.19"))
        cache.offer(bridge(), IP.of("192.0.2.19"))

        fetches shouldBe 1
        cache.device(IP.of("192.0.2.19")).shouldBeNull()
    }

    @Test
    fun a_failing_location_at_a_reused_ip_does_not_keep_the_earlier_description_alive() {
        val clock = TestClock()
        val cache = SsdpCache(fetch = { if (it.path == "/description.xml") BRIDGE else null }, clock = clock, ttl = 30.minutes)
        cache.offer(bridge(), IP.of("192.0.2.5"))

        clock.advance(20.minutes)
        cache.offer(bridge(location = "http://192.0.2.5:16021/device_info"), IP.of("192.0.2.5"))
        clock.advance(20.minutes)

        cache.device(IP.of("192.0.2.5")).shouldBeNull()
    }

    @Test
    fun a_byebye_drops_the_senders_entry_and_fetches_nothing() {
        val fetched = mutableListOf<URI>()
        val cache = SsdpCache(fetch = { fetched += it; BRIDGE }, clock = TestClock())
        cache.offer(bridge(), IP.of("192.0.2.5"))

        cache.offer(byebye(location = "http://192.0.2.5:80/other.xml"), IP.of("192.0.2.5"))
        cache.offer(byebye(location = "http://192.0.2.6:80/description.xml"), IP.of("192.0.2.6"))

        cache.device(IP.of("192.0.2.5")).shouldBeNull()
        cache.device(IP.of("192.0.2.6")).shouldBeNull()
        fetched shouldBe listOf(URI("http://192.0.2.5:80/description.xml"))
    }

    @Test
    fun a_message_without_location_is_ignored() {
        var fetches = 0
        val cache = SsdpCache(fetch = { fetches++; BRIDGE })

        cache.offer(SsdpMessage("NOTIFY * HTTP/1.1", mapOf("NT" to "x")), IP.of("192.0.2.5"))

        fetches shouldBe 0
    }

    @Test
    fun listening_never_throws_and_closing_ends_its_thread() {
        val loopback = NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress())
        val cache = SsdpCache(fetch = { BRIDGE })

        cache.listen(loopback).close()

        val threads = Thread.getAllStackTraces().keys.filter { it.name == "ssdp-${loopback.name}" && it.isAlive }
        threads.shouldBeEmpty()
    }
}

private val BRIDGE = DeviceDescription("Test Bridge (192.0.2.5)", "Example Corp", "Test Bridge", "TB001", "urn:schemas-upnp-org:device:Basic:1", "uuid:00000000-0000-4000-8000-000000000001")

private fun bridge(location: String = "http://192.0.2.5:80/description.xml") =
    SsdpMessage("HTTP/1.1 200 OK", mapOf("LOCATION" to location, "ST" to "upnp:rootdevice", "CACHE-CONTROL" to "max-age=100"))

private fun byebye(location: String) =
    SsdpMessage("NOTIFY * HTTP/1.1", mapOf("LOCATION" to location, "NT" to "upnp:rootdevice", "NTS" to "ssdp:byebye"))

private class TestClock(private var now: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock {
    override fun now(): Instant = now
    fun advance(duration: Duration) { now += duration }
}
