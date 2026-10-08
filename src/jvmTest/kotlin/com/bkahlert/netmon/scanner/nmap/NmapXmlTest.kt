package com.bkahlert.netmon.scanner.nmap

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.Status
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NmapXmlTest {

    @Test
    fun hosts_with_ip_name_status_vendor_and_mac() {
        val result = NmapXml.parse(nmapRun(UP_WITH_NAME, UP_WITHOUT_NAME, LOCALHOST))

        result.shouldContainExactly(
            Host(ip = IP.of("192.168.42.180"), name = "foo.bar", status = Status.UP, vendor = "Raspberry Pi Trading", mac = "dc:00:00:00:00:12"),
            Host(ip = IP.of("192.168.42.190"), name = null, status = Status.UP, vendor = "Raspberry Pi Trading", mac = "e4:00:00:00:00:13"),
            Host(ip = IP.of("192.168.42.33"), name = null, status = Status.UP, vendor = null, mac = null),
        )
    }

    @Test
    fun a_private_mac_has_no_vendor_but_is_read() {
        val result = NmapXml.parse(nmapRun(PRIVATE_MAC))

        result.single() shouldBe Host(ip = IP.of("192.168.42.9"), name = null, status = Status.UP, vendor = null, mac = "de:ad:be:ef:00:01")
    }

    @Test
    fun a_mac_reported_at_several_ips_identifies_none_of_them() {
        val result = NmapXml.parse(nmapRun(SHARED_MAC_A, SHARED_MAC_B, UP_WITH_NAME))

        result.map { it.ip.toString() to it.mac } shouldContainExactly listOf(
            "192.168.42.8" to null,
            "192.168.42.50" to null,
            "192.168.42.180" to "dc:00:00:00:00:12",
        )
    }

    @Test
    fun a_host_without_an_ip_address_is_left_out() {
        val result = NmapXml.parse(nmapRun(MAC_ONLY, LOCALHOST))

        result shouldHaveSize 1
    }

    @Test
    fun the_first_hostname_wins() {
        val result = NmapXml.parse(nmapRun(TWO_NAMES))

        result.single().name shouldBe "first.local"
    }

    @Test
    fun ipv6_and_down_hosts() {
        val result = NmapXml.parse(nmapRun(IPV6, DOWN))

        result should {
            it[0].ip shouldBe IP.of("fe80::1")
            it[1].status shouldBe Status.DOWN
        }
    }

    @Test
    fun an_empty_run_has_no_hosts() {
        NmapXml.parse(nmapRun()).shouldBeEmpty()
    }

    @Test
    fun the_doctype_is_not_resolved() {
        val xml = nmapRun(LOCALHOST).replace("<!DOCTYPE nmaprun>", "<!DOCTYPE nmaprun SYSTEM \"file:///nonexistent/nmap.dtd\">")

        NmapXml.parse(xml) shouldHaveSize 1
    }
}

private fun nmapRun(vararg hosts: String): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE nmaprun>
    <nmaprun scanner="nmap" args="nmap -sn -oX - 192.168.42.0/24" start="1691000916" version="7.94" xmloutputversion="1.05">
    <verbose level="0"/>
    <debugging level="0"/>
    ${hosts.joinToString("\n").prependIndent("    ")}
    <runstats><finished time="1691000919" elapsed="3.37" exit="success"/><hosts up="18" down="238" total="256"/>
    </runstats>
    </nmaprun>
""".trimIndent()

private val UP_WITH_NAME = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.180" addrtype="ipv4"/>
    <address addr="DC:00:00:00:00:12" addrtype="mac" vendor="Raspberry Pi Trading"/>
    <hostnames>
    <hostname name="foo.bar" type="PTR"/>
    </hostnames>
    <times srtt="6532" rttvar="6532" to="100000"/>
    </host>
""".trimIndent()

private val UP_WITHOUT_NAME = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.190" addrtype="ipv4"/>
    <address addr="E4:00:00:00:00:13" addrtype="mac" vendor="Raspberry Pi Trading"/>
    <hostnames>
    </hostnames>
    </host>
""".trimIndent()

private val LOCALHOST = """
    <host><status state="up" reason="localhost-response" reason_ttl="0"/>
    <address addr="192.168.42.33" addrtype="ipv4"/>
    <hostnames>
    </hostnames>
    </host>
""".trimIndent()

private val MAC_ONLY = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="00:11:22:33:44:55" addrtype="mac"/>
    </host>
""".trimIndent()

private val TWO_NAMES = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.7" addrtype="ipv4"/>
    <hostnames>
    <hostname name="first.local" type="PTR"/>
    <hostname name="second.local" type="user"/>
    </hostnames>
    </host>
""".trimIndent()

private val IPV6 = """
    <host><status state="up" reason="nd-response" reason_ttl="0"/>
    <address addr="fe80::1" addrtype="ipv6"/>
    </host>
""".trimIndent()

private val DOWN = """
    <host><status state="down" reason="no-response" reason_ttl="0"/>
    <address addr="192.168.42.8" addrtype="ipv4"/>
    </host>
""".trimIndent()

private val PRIVATE_MAC = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.9" addrtype="ipv4"/>
    <address addr="DE:AD:BE:EF:00:01" addrtype="mac"/>
    </host>
""".trimIndent()

private val SHARED_MAC_A = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.8" addrtype="ipv4"/>
    <address addr="AA:BB:CC:DD:EE:08" addrtype="mac" vendor="Apple"/>
    </host>
""".trimIndent()

private val SHARED_MAC_B = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.50" addrtype="ipv4"/>
    <address addr="AA:BB:CC:DD:EE:08" addrtype="mac" vendor="Apple"/>
    </host>
""".trimIndent()
