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

/** The CIDR representation of this [InterfaceAddress]: the interface's own address with its prefix, as in `192.168.17.42/23`. */
val InterfaceAddress.cidr: Cidr
    get() = Cidr.parse("${address.hostAddress}/$networkPrefixLength")

/** The network this [InterfaceAddress] is on: the host bits masked off, as in `192.168.16.0/23`; equal for every interface on one network. */
val InterfaceAddress.network: Cidr
    get() = networkOf(address, networkPrefixLength.toInt())

/** The network the [address] with the given [prefixLength] belongs to. */
fun networkOf(address: InetAddress, prefixLength: Int): Cidr {
    val bytes = address.address
    val mask = networkMaskOf(bytes.size, prefixLength)
    val network = InetAddress.getByAddress(bytes.zip(mask) { a, m -> a and m }.toByteArray())
    return Cidr.parse("${network.hostAddress}/$prefixLength")
}

/** A network mask of [size] bytes with the first [prefixLength] bits set. */
fun networkMaskOf(size: Int, prefixLength: Int): ByteArray = ByteArray(size) { i ->
    val bits = (prefixLength - i * 8).coerceIn(0, 8)
    ((0xff shl (8 - bits)) and 0xff).toByte()
}

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
    get() = networkMaskOf(address.address.size, networkPrefixLength.toInt())

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
