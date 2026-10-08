package com.bkahlert.netmon.display.networks

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind

class HostGroupTest {

    @Test
    fun every_kind_and_no_kind_belong_to_exactly_one_group() {
        val memberships = (Kind.entries + null).associateWith { kind -> HostGroup.entries.filter { kind in it.kinds } }

        memberships.filterValues { it.size != 1 }.shouldBeEmpty()
    }

    @Test
    fun the_groups_have_a_fixed_order() {
        val labels = HostGroup.entries.map { it.label }

        labels shouldBe listOf("Network", "Computers", "Phones & tablets", "Media", "Smart home", "Other")
    }

    @Test
    fun a_kind_falls_into_its_group() {
        val groups = listOf(Kind.ROUTER, Kind.STORAGE, Kind.SMART_WATCH, Kind.GAMING_DEVICE, Kind.HUB, Kind.GENERIC, null).map(HostGroup::of)

        groups shouldBe listOf(HostGroup.NETWORK, HostGroup.COMPUTERS, HostGroup.PHONES_AND_TABLETS, HostGroup.MEDIA, HostGroup.SMART_HOME, HostGroup.OTHER, HostGroup.OTHER)
    }

    @Test
    fun grouped_hosts_follow_the_group_order_and_leave_out_empty_groups() {
        val hosts = listOf(host("192.0.2.1", Kind.SOCKET), host("192.0.2.2"), host("192.0.2.3", Kind.ROUTER))

        val result = hosts.groupedByKind()

        result.keys.toList() shouldBe listOf(HostGroup.NETWORK, HostGroup.SMART_HOME, HostGroup.OTHER)
    }

    @Test
    fun grouped_hosts_are_ordered_by_the_numbers_of_their_ipv4_octets() {
        val hosts = listOf(host("192.0.2.200"), host("192.0.2.10"), host("192.0.2.9"), host("192.0.2.100"))

        val result = hosts.groupedByKind()

        result.ips() shouldBe listOf("192.0.2.9", "192.0.2.10", "192.0.2.100", "192.0.2.200")
    }

    @Test
    fun grouped_hosts_have_ipv6_after_ipv4_ordered_by_their_bytes() {
        val hosts = listOf(host("2001:db8::10"), host("192.0.2.200"), host("2001:db8::9"), host("192.0.2.1"))

        val result = hosts.groupedByKind()

        result.ips() shouldBe listOf("192.0.2.1", "192.0.2.200", "2001:db8::9", "2001:db8::10")
    }

    @Test
    fun grouped_hosts_with_the_same_ip_are_ordered_by_name_nameless_last() {
        val hosts = listOf(host("192.0.2.1"), host("192.0.2.1", name = "beta"), host("192.0.2.1", name = "alpha"))

        val result = hosts.groupedByKind()

        result.values.flatten().map { it.name } shouldBe listOf("alpha", "beta", null)
    }
}

private fun host(ip: String, kind: Kind? = null, name: String? = null) = Host(ip = IP.of(ip), name = name, kind = kind)

private fun Map<HostGroup, List<Host>>.ips(): List<String> = values.flatten().map { it.ip.toString() }
