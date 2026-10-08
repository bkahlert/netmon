package com.bkahlert.netmon.display.networks

import com.bkahlert.netmon.contract.Kind.AIR_CONDITIONER
import com.bkahlert.netmon.contract.Kind.AIR_PURIFIER
import com.bkahlert.netmon.contract.Kind.BUTTON
import com.bkahlert.netmon.contract.Kind.CAMERA
import com.bkahlert.netmon.contract.Kind.CIRCUIT_BOARD
import com.bkahlert.netmon.contract.Kind.COMPUTER
import com.bkahlert.netmon.contract.Kind.DOOR_BELL
import com.bkahlert.netmon.contract.Kind.DOOR_LOCK
import com.bkahlert.netmon.contract.Kind.GAMING_DEVICE
import com.bkahlert.netmon.contract.Kind.GENERIC
import com.bkahlert.netmon.contract.Kind.HUB
import com.bkahlert.netmon.contract.Kind.IP_PHONE
import com.bkahlert.netmon.contract.Kind.LAMP
import com.bkahlert.netmon.contract.Kind.LAPTOP
import com.bkahlert.netmon.contract.Kind.MONITOR
import com.bkahlert.netmon.contract.Kind.NETWORK_SWITCH
import com.bkahlert.netmon.contract.Kind.PHONE
import com.bkahlert.netmon.contract.Kind.PRINTER
import com.bkahlert.netmon.contract.Kind.ROBOT
import com.bkahlert.netmon.contract.Kind.ROUTER
import com.bkahlert.netmon.contract.Kind.SENSOR
import com.bkahlert.netmon.contract.Kind.SET_TOP_BOX
import com.bkahlert.netmon.contract.Kind.SHUTTER
import com.bkahlert.netmon.contract.Kind.SMARTPHONE
import com.bkahlert.netmon.contract.Kind.SMART_WATCH
import com.bkahlert.netmon.contract.Kind.SOCKET
import com.bkahlert.netmon.contract.Kind.SPEAKER
import com.bkahlert.netmon.contract.Kind.STORAGE
import com.bkahlert.netmon.contract.Kind.TABLET
import com.bkahlert.netmon.contract.Kind.TELEVISION
import com.bkahlert.netmon.contract.Kind.THERMOSTAT
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.IPv4
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind

/** A group of hosts the display shows together, in the order of the entries; `null` in [kinds] stands for a host without a kind. */
enum class HostGroup(val label: String, val kinds: Set<Kind?>) {
    NETWORK("Network", setOf(ROUTER, NETWORK_SWITCH)),
    COMPUTERS("Computers", setOf(COMPUTER, LAPTOP, STORAGE, CIRCUIT_BOARD, PRINTER, MONITOR)),
    PHONES_AND_TABLETS("Phones & tablets", setOf(SMARTPHONE, PHONE, IP_PHONE, TABLET, SMART_WATCH)),
    MEDIA("Media", setOf(TELEVISION, SET_TOP_BOX, SPEAKER, GAMING_DEVICE)),
    // A hub is a bridge of smart-home devices (Hue, tado, a Thread border router), not a network switch.
    SMART_HOME("Smart home", setOf(HUB, LAMP, SOCKET, SENSOR, CAMERA, THERMOSTAT, DOOR_BELL, DOOR_LOCK, BUTTON, SHUTTER, AIR_CONDITIONER, AIR_PURIFIER, ROBOT)),
    OTHER("Other", setOf(GENERIC, null));

    companion object {
        /** Returns the group that holds [kind]. */
        fun of(kind: Kind?): HostGroup = entries.single { kind in it.kinds }
    }
}

/**
 * Returns these hosts by the [HostGroup] of their kind, in the order of the groups; a group without hosts has no entry.
 *
 * The hosts of a group are ordered by IP address, IPv4 before IPv6, each by its bytes as unsigned numbers, then by name,
 * hosts without a name last.
 */
fun Iterable<Host>.groupedByKind(): Map<HostGroup, List<Host>> {
    val byGroup = groupBy { HostGroup.of(it.kind) }
    return HostGroup.entries.mapNotNull { group -> byGroup[group]?.let { group to it.sortedWith(HOST_ORDER) } }.toMap()
}

private val IP_ORDER: Comparator<IP> = compareBy<IP> { it !is IPv4 }.thenComparator { a, b -> compareUnsigned(a.bytes, b.bytes) }

private val HOST_ORDER: Comparator<Host> = compareBy(IP_ORDER) { host: Host -> host.ip }.thenBy(nullsLast()) { it.name }

private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    a.indices.intersect(b.indices).forEach { i ->
        val order = a[i].toUByte().compareTo(b[i].toUByte())
        if (order != 0) return order
    }
    return a.size.compareTo(b.size)
}
