package com.bkahlert.netmon

import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.net.InetAddress

@Serializable(with = IPSerializer::class)
actual class IP actual constructor(actual val value: String) : Comparable<IP> {
    constructor(addr: InetAddress) : this(addr.hostAddress)

    actual val bytes: UByteArray by lazy { addr.address.toUByteArray() }

    val addr: InetAddress by lazy { InetAddress.getByName(value) }

    override fun compareTo(other: IP): Int =
        BigInteger(1, addr.address).compareTo(BigInteger(1, other.addr.address))

    override fun toString(): String = value
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IP

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
