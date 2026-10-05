package com.bkahlert.netmon.router

import com.bkahlert.netmon.LinkSpeed
import com.bkahlert.netmon.mdns.FakeMdns
import com.bkahlert.netmon.mdns.service
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.slf4j.Logger
import org.slf4j.helpers.MessageFormatter

class FritzBoxHostsTest {

    @Test
    fun with_credentials_one_refresh_loads_the_whole_table() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())

            hosts.refresh()

            hosts.byMac("a8:00:00:00:00:06")?.hostName shouldBe "LEDVANCE-Hallway-TV"
            hosts.byMac("00:00:00:00:00:14")?.linkSpeed shouldBe LinkSpeed(2500)
            box.requests shouldHaveSize 2
        }
    }

    @Test
    fun by_ip_prefers_the_active_entry() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())
            hosts.refresh()

            hosts.byIp("198.51.100.13")?.mac shouldBe "02:aa:bb:cc:00:16"
        }
    }

    @Test
    fun without_credentials_a_mac_is_looked_up_in_the_background_once_per_ten_minutes() {
        FakeFritzBox(unauthenticated = mapOf("GetSpecificHostEntry" to ENTRY)).use { box ->
            val clock = TestClock()
            FritzBoxHosts({ Tr064Client(box.base, null) }, credentials = null, clock = clock).use { hosts ->
                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
                eventually { hosts.byMac("a8:00:00:00:00:06") != null }

                hosts.byMac("a8:00:00:00:00:06")?.hostName shouldBe "LEDVANCE-Hallway-TV"
                box.requests shouldHaveSize 1
                clock.advance(11.minutes)
                hosts.byMac("a8:00:00:00:00:06")?.hostName shouldBe "LEDVANCE-Hallway-TV"
                eventually { box.requests.size == 2 }

                hosts.byMac("a8:00:00:00:00:06")?.linkSpeed.shouldBeNull()
            }
        }
    }

    @Test
    fun without_credentials_the_caller_never_waits_for_the_box() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        FritzBoxHosts({ started.countDown(); release.await(); null }, credentials = null, clock = TestClock()).use { hosts ->
            hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
            hosts.byIp("192.0.2.70").shouldBeNull()

            started.await(5, TimeUnit.SECONDS) shouldBe true
        }
        liveWorkers() shouldHaveSize 0
    }

    @Test
    fun an_unknown_mac_is_an_answer_and_not_a_failure() {
        val log = RecordingLogger()
        FakeFritzBox(unauthenticated = mapOf("GetSpecificHostEntry" to ENTRY), unknownMacs = setOf("02:00:00:00:00:01")).use { box ->
            FritzBoxHosts({ Tr064Client(box.base, null) }, credentials = null, clock = TestClock(), logger = log.logger).use { hosts ->
                hosts.byMac("02:00:00:00:00:01").shouldBeNull()
                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
                eventually { hosts.byMac("a8:00:00:00:00:06") != null }

                box.requests shouldHaveSize 2
                hosts.byMac("02:00:00:00:00:01").shouldBeNull()
                eventually { box.requests.size == 2 }
                log.events shouldHaveSize 0
            }
        }
    }

    @Test
    fun a_failing_lookup_is_logged_once_and_so_is_the_recovery() {
        val log = RecordingLogger()
        val dead = FakeFritzBox().also { it.close() }
        FakeFritzBox(unauthenticated = mapOf("GetSpecificHostEntry" to ENTRY)).use { live ->
            val box = AtomicReference(dead)
            val calls = AtomicInteger()
            FritzBoxHosts({ val target = box.get(); calls.incrementAndGet(); Tr064Client(target.base, null) }, credentials = null, clock = TestClock(), logger = log.logger).use { hosts ->
                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
                eventually { log.events.size == 1 }
                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
                eventually { calls.get() == 2 }
                box.set(live)

                hosts.byMac("a8:00:00:00:00:07").shouldBeNull()
                eventually { hosts.byMac("a8:00:00:00:00:07") != null }

                log.events.map { it.substringBefore(' ') } shouldBe listOf("WARN", "INFO")
            }
        }
    }

    @Test
    fun a_failed_lookup_can_be_asked_again_as_soon_as_its_failure_is_logged() {
        val warned = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val log = RecordingLogger { warned.countDown(); resume.await(3, TimeUnit.SECONDS) }
        val dead = FakeFritzBox().also { it.close() }
        val calls = AtomicInteger()
        FritzBoxHosts({ calls.incrementAndGet(); Tr064Client(dead.base, null) }, credentials = null, clock = TestClock(), logger = log.logger).use { hosts ->
            try {
                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
                warned.await(5, TimeUnit.SECONDS) shouldBe true

                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
            } finally {
                resume.countDown()
            }

            eventually { calls.get() == 2 }
        }
    }

    @Test
    fun without_credentials_expired_lookups_are_dropped_when_a_new_one_is_queued() {
        FakeFritzBox(unauthenticated = mapOf("GetSpecificHostEntry" to ENTRY)).use { box ->
            val clock = TestClock()
            FritzBoxHosts({ Tr064Client(box.base, null) }, credentials = null, clock = clock).use { hosts ->
                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
                eventually { hosts.byMac("a8:00:00:00:00:06") != null }
                clock.advance(11.minutes)

                hosts.byMac("a8:00:00:00:00:07").shouldBeNull()

                hosts.byMac("a8:00:00:00:00:06").shouldBeNull()
            }
        }
    }

    @Test
    fun after_a_transport_failure_the_rest_of_the_batch_waits_for_the_next_request() {
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        FakeFritzBox(unauthenticated = mapOf("GetSpecificHostEntry" to ENTRY)).use { live ->
            val client = {
                when (calls.incrementAndGet()) {
                    1 -> { release.await(); Tr064Client(live.base, null) }
                    2 -> throw java.io.IOException("down")
                    else -> Tr064Client(live.base, null)
                }
            }
            FritzBoxHosts(client, credentials = null, clock = TestClock(), logger = RecordingLogger().logger).use { hosts ->
                hosts.byMac("a8:00:00:00:00:00")
                eventually { calls.get() == 1 }
                hosts.byMac("a8:00:00:00:00:01")
                hosts.byMac("a8:00:00:00:00:02")
                release.countDown()
                eventually { calls.get() == 2 }

                hosts.byMac("a8:00:00:00:00:03")
                eventually { hosts.byMac("a8:00:00:00:00:03") != null }

                calls.get() shouldBe 3
                hosts.byMac("a8:00:00:00:00:02").shouldBeNull()
            }
        }
    }

    @Test
    fun a_failing_box_leaves_the_last_table_in_place() {
        val box = FakeFritzBox()
        val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())
        hosts.refresh()
        box.close()

        hosts.refresh()

        hosts.byMac("a8:00:00:00:00:06").shouldNotBeNull()
    }

    @Test
    fun a_mac_is_found_whatever_its_case() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())
            hosts.refresh()

            hosts.byMac("A8:00:00:00:00:06")?.hostName shouldBe "LEDVANCE-Hallway-TV"
        }
    }

    @Test
    fun a_list_that_is_not_xml_leaves_the_last_table_in_place() {
        FakeFritzBox().use { good ->
            FakeFritzBox(hostList = "<List><Item><MACAddress>").use { broken ->
                val boxes = ArrayDeque(listOf(good, broken))
                val hosts = FritzBoxHosts({ boxes.removeFirstOrNull()?.let { Tr064Client(it.base, it.credentials) } }, good.credentials, TestClock())
                hosts.refresh()

                hosts.refresh()

                hosts.byMac("a8:00:00:00:00:06").shouldNotBeNull()
            }
        }
    }

    @Test
    fun a_client_that_cannot_be_built_leaves_the_last_table_in_place() {
        FakeFritzBox().use { box ->
            var calls = 0
            val hosts = FritzBoxHosts({ if (calls++ == 0) Tr064Client(box.base, box.credentials) else throw IllegalArgumentException("bad uri") }, box.credentials, TestClock())
            hosts.refresh()

            hosts.refresh()

            hosts.byMac("a8:00:00:00:00:06").shouldNotBeNull()
        }
    }

    @Test
    fun a_started_table_loads_and_close_ends_its_thread() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock(), refreshEvery = 1.hours)

            hosts.start()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (hosts.byMac("a8:00:00:00:00:06") == null && System.nanoTime() < deadline) Thread.sleep(10)
            hosts.byMac("a8:00:00:00:00:06").shouldNotBeNull()

            hosts.close()

            liveWorkers() shouldHaveSize 0
        }
    }

    @Test
    fun close_ends_the_thread_even_while_a_refresh_is_failing() {
        val hosts = FritzBoxHosts({ throw InterruptedException() }, Credentials("netmon", "secret"), TestClock(), refreshEvery = 1.hours)

        hosts.start()
        hosts.close()

        liveWorkers() shouldHaveSize 0
    }

    @Test
    fun the_endpoint_comes_from_the_setting_then_the_tr064_record_then_the_default_name() {
        val tr064 = service("tr064", "198-51-100-1", "fritz.box.", 49000, "198.51.100.1", "path" to "http://fritz.box:49000/tr64desc.xml", "ipv4" to "198.51.100.1")

        FritzBoxEndpoint.discover("http://10.0.0.1:49000", FakeMdns(tr064)) shouldBe URI("http://10.0.0.1:49000")
        FritzBoxEndpoint.discover(null, FakeMdns(tr064)) shouldBe URI("http://198.51.100.1:49000")
        FritzBoxEndpoint.discover(null, FakeMdns()) shouldBe URI("http://fritz.box:49000")
    }
}

private class TestClock(start: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock {
    @Volatile
    private var now: Instant = start

    override fun now(): Instant = now
    fun advance(duration: kotlin.time.Duration) { now += duration }
}

private const val ENTRY = "<NewIPAddress>192.0.2.70</NewIPAddress><NewActive>1</NewActive><NewHostName>LEDVANCE-Hallway-TV</NewHostName><NewInterfaceType>802.11</NewInterfaceType>"

private fun eventually(condition: () -> Boolean) {
    val deadline = System.nanoTime() + 5_000_000_000L
    while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
    condition() shouldBe true
}

private fun liveWorkers() = Thread.getAllStackTraces().keys.filter { it.name == "fritzbox-hosts" && it.isAlive }

private class RecordingLogger(private val afterEvent: () -> Unit = {}) {
    val events = CopyOnWriteArrayList<String>()
    val logger: Logger = Proxy.newProxyInstance(Logger::class.java.classLoader, arrayOf(Logger::class.java)) { _, method, args ->
        if (method.name in setOf("info", "warn", "error")) {
            val message = args.orEmpty().filterIsInstance<String>().firstOrNull().orEmpty()
            events += "${method.name.uppercase()} ${MessageFormatter.arrayFormat(message, args.orEmpty().drop(1).toTypedArray()).message}"
            afterEvent()
        }
        if (method.returnType == Boolean::class.javaPrimitiveType) false else null
    } as Logger
}

