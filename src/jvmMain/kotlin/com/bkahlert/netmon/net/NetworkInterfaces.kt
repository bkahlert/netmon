package com.bkahlert.netmon.net

import com.bkahlert.kommons.dec
import com.bkahlert.kommons.inc
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.IP
import java.math.BigInteger
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

/** The number of host bits. */
val InterfaceAddress.hostBits: Int
    get() = address.address.size * 8 - networkPrefixLength

/** The maximum number of hosts in this network. */
val InterfaceAddress.maxHosts: BigInteger
    get() = hostBits.toBigInteger().pow(8).minus(BigInteger.ONE).minus(BigInteger.ONE)

/** The [IP] of this [InterfaceAddress]. */
val InterfaceAddress.ip: IP
    get() = IP.of(address.hostAddress)

/** The CIDR representation of this [InterfaceAddress]. */
val InterfaceAddress.cidr: Cidr
    get() = Cidr.parse("${address.hostAddress}/$networkPrefixLength")

/** The [NetworkInterface] that has the [InetAddress] bound to it. */
val InterfaceAddress.networkInterface: NetworkInterface?
    get() = NetworkInterface.getByInetAddress(address)

// TODO test
/**
 * The network mask of this [InterfaceAddress], that is,
 * a byte array with the same size as the [InterfaceAddress.address], and
 * the first [InterfaceAddress.getNetworkPrefixLength] bits beging 1.
 */
val InterfaceAddress.networkMask: ByteArray
    get() = ByteArray(address.address.size) { i ->
        if (i < networkPrefixLength / 8) {
            0xff.toByte()
        } else {
            (0xff shl 8 - (networkPrefixLength - i * 8)).toByte()
        }
    }

/**
 * The network address mask of this [InterfaceAddress], that is,
 * the [networkMask] applied to this address.
 */
val InterfaceAddress.networkAddress: InetAddress
    get() = InetAddress.getByAddress(address.address.zip(networkMask) { a, m -> a and m }.toByteArray())

class IPRange(
    override val start: IP,
    override val endInclusive: IP,
) : ClosedRange<IP>, Iterable<IP> {
    override fun iterator(): Iterator<IP> = iterator {
        var current = start
        while (current <= endInclusive) {
            yield(current)
            current = IP.of(current.bytes.inc())
        }
    }
}

operator fun IP.rangeTo(other: IP): IPRange = IPRange(this, other)

val InterfaceAddress.ipRange: IPRange
    get() = IP.of(networkAddress.address.inc())..IP.of(networkAddress.address.zip(networkMask) { a, m -> a.or(m.inv()) }.toByteArray().dec())
