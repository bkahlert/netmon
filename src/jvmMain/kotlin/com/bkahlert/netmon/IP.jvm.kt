package com.bkahlert.netmon

import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

@Serializable(with = IPSerializer::class)
actual sealed interface IP : Comparable<IP> {
    actual val bytes: ByteArray

    actual companion object {

        actual fun of(bytes: ByteArray): IP = when (val address = InetAddress.getByAddress(bytes)) {
            is Inet4Address -> IPv4(address)
            is Inet6Address -> IPv6(address)
            else -> throw IllegalArgumentException("Unexpected IP address type: $address")
        }

        actual fun of(text: String): IP = when (val address = InetAddress.getByName(text)) {
            is Inet4Address -> IPv4(address)
            is Inet6Address -> IPv6(address)
            else -> throw IllegalArgumentException("Unexpected IP address type: $address")
        }
    }
}

actual class IPv4(val addr: Inet4Address) : IP {
    actual constructor(bytes: ByteArray) : this(
        InetAddress.getByAddress(bytes
            .also { require(it.size == 4) { "4 bytes expected but ${bytes.size} found" } })
            .let { check(it is Inet4Address) { "Unexpected IP address type: $it" }; it },
    )

    override val bytes: ByteArray by lazy { addr.address }
    private val text by lazy { addr.toString().removePrefix("/") }

    override fun compareTo(other: IP): Int = BigInteger(1, bytes).compareTo(BigInteger(1, other.bytes))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IP

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = text

    actual companion object {
        actual const val SIZE_BITS: Int = 32
        actual const val SIZE_BYTES: Int = 4
    }
}

actual class IPv6(val addr: Inet6Address) : IP {
    actual constructor(bytes: ByteArray) : this(
        InetAddress.getByAddress(bytes
            .also { require(it.size == 16) { "16 bytes expected but ${bytes.size} found" } })
            .let { check(it is Inet6Address) { "Unexpected IP address type: $it" }; it },
    )

    override val bytes: ByteArray by lazy { addr.address }
    private val text by lazy { IPv6Compressor.compress(addr.toString().removePrefix("/")) }

    override fun compareTo(other: IP): Int = BigInteger(1, bytes).compareTo(BigInteger(1, other.bytes))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IP

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = text

    actual companion object {
        actual const val SIZE_BITS: Int = 128
        actual const val SIZE_BYTES: Int = 16
    }
}
