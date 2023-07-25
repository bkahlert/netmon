package com.bkahlert.netmon

import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InterfaceAddress

/** The network size in bits. */
val InterfaceAddress.networkSize: Int
    get() = address.address.size * 8 - networkPrefixLength

/** The maximum number of hosts in this network. */
val InterfaceAddress.maxHosts: BigInteger
    get() = networkSize.toBigInteger().pow(8).minus(BigInteger.ONE).minus(BigInteger.ONE)

/** The CIDR representation of this [InterfaceAddress]. */
val InterfaceAddress.cidr: Cidr
    get() = Cidr("${address.toString().removePrefix("/")}/$networkPrefixLength")


/** Whether this [InterfaceAddress] is elligible to be scanned. */
val InterfaceAddress.scanElligable: Boolean
    get() = address.scanElligable

/** Whether this [InetAddress] is part of a network elligible to be scanned. */
val InetAddress.scanElligable: Boolean
    get() = when (this) {
        is Inet4Address -> isSiteLocalAddress
        is Inet6Address -> isLinkLocalAddress
        else -> false
    }

/** Whether this [Inet4Address] is part of a network elligible to be scanned. */
val Inet4Address.scanElligable: Boolean get() = isSiteLocalAddress

/** Whether this [Inet6Address] is part of a network elligible to be scanned. */
val Inet6Address.scanElligable: Boolean get() = isLinkLocalAddress
