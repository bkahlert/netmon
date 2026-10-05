package com.bkahlert.netmon.router

import com.bkahlert.netmon.LinkSpeed
import com.bkahlert.netmon.mdns.FakeMdns
import com.bkahlert.netmon.mdns.service
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.net.URI
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FritzBoxHostsTest {

    @Test
    fun with_credentials_one_refresh_loads_the_whole_table() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())

            hosts.refresh()

            hosts.byMac("a8:80:55:37:e5:c6")?.hostName shouldBe "LEDVANCE-Sideboard-TV"
            hosts.byMac("00:e0:4e:3a:5f:84")?.linkSpeed shouldBe LinkSpeed(2500)
            box.requests shouldHaveSize 2
        }
    }

    @Test
    fun by_ip_prefers_the_active_entry() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())
            hosts.refresh()

            hosts.byIp("192.168.16.13")?.mac shouldBe "de:c8:ff:43:fc:54"
        }
    }

    @Test
    fun without_credentials_a_mac_is_looked_up_once_per_ten_minutes() {
        FakeFritzBox(unauthenticated = mapOf("GetSpecificHostEntry" to "<NewIPAddress>192.168.17.70</NewIPAddress><NewActive>1</NewActive><NewHostName>LEDVANCE-Sideboard-TV</NewHostName><NewInterfaceType>802.11</NewInterfaceType>")).use { box ->
            val clock = TestClock()
            val hosts = FritzBoxHosts({ Tr064Client(box.base, null) }, credentials = null, clock = clock)

            hosts.byMac("a8:80:55:37:e5:c6")?.hostName shouldBe "LEDVANCE-Sideboard-TV"
            hosts.byMac("a8:80:55:37:e5:c6")?.hostName shouldBe "LEDVANCE-Sideboard-TV"
            clock.advance(11.minutes)
            hosts.byMac("a8:80:55:37:e5:c6")

            box.requests shouldHaveSize 2
            hosts.byMac("a8:80:55:37:e5:c6")?.linkSpeed.shouldBeNull()
        }
    }

    @Test
    fun a_failing_box_leaves_the_last_table_in_place() {
        val box = FakeFritzBox()
        val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())
        hosts.refresh()
        box.close()

        hosts.refresh()

        hosts.byMac("a8:80:55:37:e5:c6").shouldNotBeNull()
    }

    @Test
    fun a_mac_is_found_whatever_its_case() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock())
            hosts.refresh()

            hosts.byMac("A8:80:55:37:E5:C6")?.hostName shouldBe "LEDVANCE-Sideboard-TV"
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

                hosts.byMac("a8:80:55:37:e5:c6").shouldNotBeNull()
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

            hosts.byMac("a8:80:55:37:e5:c6").shouldNotBeNull()
        }
    }

    @Test
    fun a_started_table_loads_and_close_ends_its_thread() {
        FakeFritzBox().use { box ->
            val hosts = FritzBoxHosts({ Tr064Client(box.base, box.credentials) }, box.credentials, TestClock(), refreshEvery = 1.hours)

            hosts.start()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (hosts.byMac("a8:80:55:37:e5:c6") == null && System.nanoTime() < deadline) Thread.sleep(10)
            hosts.byMac("a8:80:55:37:e5:c6").shouldNotBeNull()

            hosts.close()

            Thread.getAllStackTraces().keys.filter { it.name == "fritzbox-hosts" && it.isAlive } shouldHaveSize 0
        }
    }

    @Test
    fun close_ends_the_thread_even_while_a_refresh_is_failing() {
        val hosts = FritzBoxHosts({ throw InterruptedException() }, Credentials("netmon", "secret"), TestClock(), refreshEvery = 1.hours)

        hosts.start()
        hosts.close()

        Thread.getAllStackTraces().keys.filter { it.name == "fritzbox-hosts" && it.isAlive } shouldHaveSize 0
    }

    @Test
    fun the_endpoint_comes_from_the_setting_then_the_tr064_record_then_the_default_name() {
        val tr064 = service("tr064", "192-168-16-1", "fritz.box.", 49000, "192.168.16.1", "path" to "http://fritz.box:49000/tr64desc.xml", "ipv4" to "192.168.16.1")

        FritzBoxEndpoint.discover("http://10.0.0.1:49000", FakeMdns(tr064)) shouldBe URI("http://10.0.0.1:49000")
        FritzBoxEndpoint.discover(null, FakeMdns(tr064)) shouldBe URI("http://192.168.16.1:49000")
        FritzBoxEndpoint.discover(null, FakeMdns()) shouldBe URI("http://fritz.box:49000")
    }
}

private class TestClock(private var now: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock {
    override fun now(): Instant = now
    fun advance(duration: kotlin.time.Duration) { now += duration }
}
