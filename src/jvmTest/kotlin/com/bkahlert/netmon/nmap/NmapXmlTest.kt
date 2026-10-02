package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NmapXmlTest {

    @Test
    fun hosts_with_ip_name_status_and_vendor() {
        val result = NmapXml.parse(nmapRun(UP_WITH_NAME, UP_WITHOUT_NAME, LOCALHOST))

        result.shouldContainExactly(
            Host(ip = IP.of("192.168.42.180"), name = "foo.bar", status = Status.UP, vendor = "Raspberry Pi Trading"),
            Host(ip = IP.of("192.168.42.190"), name = null, status = Status.UP, vendor = "Raspberry Pi Trading"),
            Host(ip = IP.of("192.168.42.33"), name = null, status = Status.UP, vendor = null),
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
    <address addr="DC:A6:32:A5:BA:B6" addrtype="mac" vendor="Raspberry Pi Trading"/>
    <hostnames>
    <hostname name="foo.bar" type="PTR"/>
    </hostnames>
    <times srtt="6532" rttvar="6532" to="100000"/>
    </host>
""".trimIndent()

private val UP_WITHOUT_NAME = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.190" addrtype="ipv4"/>
    <address addr="E4:5F:01:34:81:39" addrtype="mac" vendor="Raspberry Pi Trading"/>
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
