package com.bkahlert.netmon.net

import com.bkahlert.netmon.Cidr
import java.math.BigInteger
import java.net.InterfaceAddress

/** The number of host bits. */
val InterfaceAddress.hostBits: Int
    get() = address.address.size * 8 - networkPrefixLength

/** The maximum number of hosts in this network. */
val InterfaceAddress.maxHosts: BigInteger
    get() = hostBits.toBigInteger().pow(8).minus(BigInteger.ONE).minus(BigInteger.ONE)

/** The CIDR representation of this [InterfaceAddress]. */
val InterfaceAddress.cidr: Cidr
    get() = Cidr("${address.hostAddress}/$networkPrefixLength")
