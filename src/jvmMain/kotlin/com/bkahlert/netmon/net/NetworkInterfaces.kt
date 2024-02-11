package com.bkahlert.netmon.net

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.IP
import java.math.BigInteger
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface

/** The number of host bits. */
val InterfaceAddress.hostBits: Int
    get() = address.address.size * 8 - networkPrefixLength

/** The maximum number of hosts in this network. */
val InterfaceAddress.maxHosts: BigInteger
    get() = hostBits.toBigInteger().pow(8).minus(BigInteger.ONE).minus(BigInteger.ONE)

/** The [IP] of this [InterfaceAddress]. */
val InterfaceAddress.ip: IP
    get() = IP(address.hostAddress)

/** The CIDR representation of this [InterfaceAddress]. */
val InterfaceAddress.cidr: Cidr
    get() = Cidr("${address.hostAddress}/$networkPrefixLength")

/** The [NetworkInterface] that has the [InetAddress] bound to it. */
val InterfaceAddress.networkInterface: NetworkInterface?
    get() = NetworkInterface.getByInetAddress(address)

// TODO test
/**
 * The network mask of this [InterfaceAddress], that is,
 * a byte array with the same size as the [InterfaceAddress.address], and
 * the first [InterfaceAddress.getNetworkPrefixLength] bits beging 1.
 */
val InterfaceAddress.networkMask: UByteArray
    get() = UByteArray(address.address.size) { i ->
        if (i < networkPrefixLength / 8) {
            UByte.MAX_VALUE
        } else {
            (0xFFu shl 8 - (networkPrefixLength - i * 8)).toUByte()
        }
    }

/**
 * The network address mask of this [InterfaceAddress], that is,
 * the [networkMask] applied to this address.
 */
val InterfaceAddress.networkAddress: InetAddress
    get() = InetAddress.getByAddress(address.bytes.zip(networkMask) { a, m -> a and m }.toUByteArray().asByteArray())

class IPRange(
    override val start: IP,
    override val endInclusive: IP,
) : ClosedRange<IP>, Iterable<IP> {
    override fun iterator(): Iterator<IP> = iterator {
        var current = start
        while (current <= endInclusive) {
            yield(current)
            current = IP(InetAddress.getByAddress(current.bytes.inc().asByteArray()))
        }
    }
}

operator fun IP.rangeTo(other: IP): IPRange = IPRange(this, other)

val InterfaceAddress.ipRange: IPRange
    get() = IP(networkAddress.inc()).rangeTo(
        IP(
            InetAddress.getByAddress(
                networkAddress.bytes.zip(networkMask) { a, m -> a.or(m.inv()) }.toUByteArray().dec().asByteArray()
            )
        )
    )

// TODO test

/**
 * The network address of this [InterfaceAddress], that is,
 * a byte array with the same size as the [InterfaceAddress.address], and
 * the first [InterfaceAddress.getNetworkPrefixLength] bits beging 1.
 */
val InetAddress.bytes: UByteArray
    get() = address.asUByteArray()

operator fun InetAddress.inc(): InetAddress =
    InetAddress.getByAddress(bytes.inc().asByteArray())

operator fun InetAddress.dec(): InetAddress =
    InetAddress.getByAddress(bytes.dec().asByteArray())

/** Returns a [UByteArray] representing the numeric value of this [UByteArray] incremented by one. */
private operator fun UByteArray.inc(): UByteArray {
    val result = copyOf()  // Copy the array
    var carry = 1  // Initialize carry

    // Loop from least significant byte back to the most significant byte
    for (i in size - 1 downTo 0) {
        val value = get(i).toInt() + carry
        if (value > 0xFF) {  // Overflow occurs
            result[i] = 0u  // Wrap around to 0
            carry = 1  // Set carry for next iteration
        } else {
            result[i] = value.toUByte()
            carry = 0  // Reset carry
            break
        }
    }

    // If carry is still 1, prepend a new element with value 1
    return if (carry == 1) {
        UByteArray(size + 1) { index -> if (index == 0) 1u else result[index - 1] }
    } else {
        result
    }
}

/** Returns a [UByteArray] representing the numeric value of this [UByteArray] decremented by one. */
private operator fun UByteArray.dec(): UByteArray {
    if (all { it == UByte.MIN_VALUE }) throw ArithmeticException("Cannot decrement zero UByteArray.")

    val result = copyOf()  // Copy the array
    var carry = 1  // Initialize carry

    // Loop from least significant byte back to the most significant byte
    for (i in size - 1 downTo 0) {
        val value = get(i).toInt() - carry
        if (value < 0) {  // Underflow occurs
            result[i] = 0xFFu  // Wrap around to 255
            carry = 1  // Set carry for next iteration
        } else {
            result[i] = value.toUByte()
            carry = 0  // Reset carry
        }
    }

    return result
}
