package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.router.RouterHostLookup

/**
 * What the router knows about a host: its DHCP hostname, a friendly name a person set, the classes, how and how fast
 * it is attached, and its MAC when nmap saw none.
 *
 * A host is matched by MAC; a host without one by IP, and only while the entry is active, so a stale lease never names
 * a new device.
 */
class RouterClues(private val hosts: RouterHostLookup) : ClueSource {

    override fun clues(host: Host): List<Clue> {
        val mac = host.mac
        val entry = if (mac != null) hosts.byMac(mac) else hosts.byIp(host.ip.toString())?.takeIf { it.active }
        if (entry == null) return emptyList()
        return buildList {
            entry.friendlyName?.takeIf { it != entry.hostName }?.let { add(Clue.Name(it, Source.USER)) }
            entry.hostName?.let { add(Clue.Name(it, Source.ROUTER)) }
            entry.deviceClassUser?.kind()?.let { add(Clue.DeviceKind(it, Source.USER)) }
            entry.deviceClass?.kind()?.let { add(Clue.DeviceKind(it, Source.ROUTER)) }
            entry.link?.let { add(Clue.Attachment(it, Source.ROUTER)) }
            entry.linkSpeed?.let { add(Clue.Speed(it, Source.ROUTER)) }
            if (mac == null) entry.mac?.let { add(Clue.Mac(it, Source.ROUTER)) }
        }
    }

    private fun String.kind(): Kind? = Kind.of(this).takeUnless { it == Kind.GENERIC }
}
