package com.bkahlert.netmon

import java.math.BigInteger
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
