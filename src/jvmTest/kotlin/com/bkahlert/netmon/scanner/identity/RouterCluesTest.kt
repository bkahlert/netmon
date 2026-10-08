package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed
import com.bkahlert.netmon.contract.invoke
import com.bkahlert.netmon.scanner.discovery.router.RouterHost
import com.bkahlert.netmon.scanner.discovery.router.RouterHostLookup
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import kotlin.test.Test

class RouterCluesTest {

    @Test
    fun a_matched_entry_gives_names_link_and_speed() {
        val result = RouterClues(table(LAMP)).clues(Host(mac = LAMP_MAC))

        result shouldContainExactly listOf(
            Clue.Name("lamp-kitchen", Source.ROUTER),
            Clue.Attachment(Link.WIFI, Source.ROUTER),
            Clue.Speed(LinkSpeed(57), Source.ROUTER),
        )
    }

    @Test
    fun a_friendly_name_that_differs_from_the_hostname_is_a_user_name() {
        val result = RouterClues(table(LAPTOP)).clues(Host(mac = LAPTOP_MAC))

        result shouldContain Clue.Name("Laptop A", Source.USER)
        result shouldContain Clue.Name("laptop-a", Source.ROUTER)
    }

    @Test
    fun classes_other_than_generic_are_user_or_router_kinds() {
        val result = RouterClues(table(CLASSIFIED)).clues(Host(mac = CLASSIFIED_MAC))

        result shouldContain Clue.DeviceKind(Kind.STORAGE, Source.USER)
        result shouldContain Clue.DeviceKind(Kind.PRINTER, Source.ROUTER)
        RouterClues(table(LAMP)).clues(Host(mac = LAMP_MAC)).filterIsInstance<Clue.DeviceKind>().shouldBeEmpty()
    }

    @Test
    fun a_host_without_mac_is_matched_by_ip_only_while_the_entry_is_active_and_takes_the_mac() {
        val result = RouterClues(table(LAMP)).clues(Host(ip = LAMP_IP, mac = null))
        val stale = RouterClues(table(LAMP.copy(active = false))).clues(Host(ip = LAMP_IP, mac = null))

        result shouldContain Clue.Mac(LAMP_MAC, Source.ROUTER)
        stale.shouldBeEmpty()
    }

    @Test
    fun a_host_with_a_mac_the_router_does_not_know_gets_nothing_from_an_ip_match() {
        val result = RouterClues(table(LAMP)).clues(Host(ip = LAMP_IP, mac = "02:aa:bb:cc:00:99"))

        result.shouldBeEmpty()
    }

    @Test
    fun a_new_device_at_the_ip_of_an_inactive_entry_does_not_inherit_its_name() {
        val result = RouterClues(table(LAMP.copy(active = false))).clues(Host(ip = LAMP_IP, mac = "02:aa:bb:cc:00:99"))

        result.shouldBeEmpty()
    }

    @Test
    fun an_inactive_entry_still_names_the_host_with_its_mac() {
        val result = RouterClues(table(LAPTOP.copy(active = false))).clues(Host(ip = "192.0.2.99", mac = LAPTOP_MAC))

        result shouldContain Clue.Name("Laptop A", Source.USER)
        result shouldContain Clue.Name("laptop-a", Source.ROUTER)
    }

    @Test
    fun a_host_with_a_mac_never_takes_the_routers() {
        val result = RouterClues(table(LAMP)).clues(Host(ip = LAMP_IP, mac = LAMP_MAC))

        result.filterIsInstance<Clue.Mac>().shouldBeEmpty()
    }

    @Test
    fun a_friendly_name_equal_to_the_hostname_is_no_user_name() {
        val result = RouterClues(table(LAMP)).clues(Host(mac = LAMP_MAC))

        result shouldNotContain Clue.Name("lamp-kitchen", Source.USER)
    }

    @Test
    fun a_zero_speed_gives_no_speed_clue() {
        val result = RouterClues(table(LAMP.copy(speed = 0))).clues(Host(mac = LAMP_MAC))

        result.filterIsInstance<Clue.Speed>().shouldBeEmpty()
        result shouldNotContain Clue.Attachment(Link.ETHERNET, Source.ROUTER)
    }
}

private const val LAMP_MAC = "02:aa:bb:cc:00:01"
private const val LAMP_IP = "192.0.2.70"
private const val LAPTOP_MAC = "02:aa:bb:cc:00:02"
private const val CLASSIFIED_MAC = "02:aa:bb:cc:00:03"

private val LAMP = RouterHost(LAMP_MAC, LAMP_IP, true, "lamp-kitchen", "lamp-kitchen", "802.11", 57, 0, false, "Generic", "Generic")
private val LAPTOP = RouterHost(LAPTOP_MAC, "192.0.2.11", true, "laptop-a", "Laptop A", "802.11", 1088, 0, false, "Generic", "Generic")
private val CLASSIFIED = RouterHost(CLASSIFIED_MAC, "192.0.2.13", true, "device-13", "device-13", "Ethernet", 2500, 1, false, "Printer", "Storage")

private fun table(vararg hosts: RouterHost) = object : RouterHostLookup {
    override fun byMac(mac: String): RouterHost? = hosts.firstOrNull { it.mac == mac.lowercase() }
    override fun byIp(ip: String): RouterHost? = hosts.filter { it.ip == ip }.maxByOrNull { it.active }
}
