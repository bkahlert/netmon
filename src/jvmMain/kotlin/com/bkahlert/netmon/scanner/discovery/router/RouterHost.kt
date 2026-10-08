package com.bkahlert.netmon.scanner.discovery.router

import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed

/** One entry of the FRITZ!Box host table; [mac] is lowercase with colons. */
data class RouterHost(
    val mac: String?,
    val ip: String?,
    val active: Boolean,
    val hostName: String?,
    val friendlyName: String?,
    val interfaceType: String?,
    val speed: Int,
    val port: Int,
    val guest: Boolean,
    val deviceClass: String?,
    val deviceClassUser: String?,
) {
    val link: Link? get() = when (interfaceType) {
        "Ethernet" -> Link.ETHERNET
        "802.11" -> Link.WIFI
        else -> null
    }

    /** The box's link rate to the host in Mbit/s, `null` when the box reports none. */
    val linkSpeed: LinkSpeed? get() = speed.takeIf { it > 0 }?.let(::LinkSpeed)
}

/** What the identity rules need from the router's host table. */
interface RouterHostLookup {
    fun byMac(mac: String): RouterHost?
    fun byIp(ip: String): RouterHost?
}
