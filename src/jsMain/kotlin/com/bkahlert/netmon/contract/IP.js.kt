package com.bkahlert.netmon.contract

import kotlinx.serialization.Serializable

@JsModule("ipaddr.js")
@JsNonModule
private external object Ipaddr {
    fun process(text: String): IpaddrAddress
    fun fromByteArray(bytes: Array<Int>): IpaddrAddress
}

private external interface IpaddrAddress {
    fun toByteArray(): Array<Int>
    fun toNormalizedString(): String
}

private fun sanitizeAddress(text: String): String {
    val characters = if (':' in text) "0123456789abcdefABCDEF:." else "0123456789."
    val sanitized = text
        .dropWhile { it !in characters }
        .dropLastWhile { it !in characters }
    require(sanitized.isNotEmpty() && sanitized.all { it in characters }) { "Invalid IP address: $text" }
    if ('.' !in sanitized) {
        require(':' in sanitized) { "Invalid IP address: $text" }
        return sanitized
    }

    val octets = sanitized.substringAfterLast(':').split('.')
    require(octets.size == 4) { "Invalid IPv4 address: $text" }
    // ipaddr.js treats leading-zero octets as octal, not decimal.
    val decimal = octets.joinToString(".") {
        require(it.isNotEmpty() && it.all { digit -> digit in '0'..'9' }) { "Invalid IPv4 address: $text" }
        it.toUByte().toString()
    }
    return if (':' in sanitized) "${sanitized.substringBeforeLast(':')}:$decimal" else decimal
}

private fun ByteArray.toUnsignedOctets(): Array<Int> = Array(size) { this[it].toInt() and 0xff }

@Serializable(with = IPSerializer::class)
actual sealed interface IP : Comparable<IP> {
    actual val bytes: ByteArray

    actual companion object {

        actual fun of(bytes: ByteArray): IP = when (bytes.size) {
            4 -> IPv4(bytes)
            16 -> IPv6(bytes)
            else -> throw IllegalArgumentException("Invalid IP address length: ${bytes.size}")
        }

        actual fun of(text: String): IP = of(Ipaddr.process(sanitizeAddress(text)).toByteArray().map { it.toByte() }.toByteArray())
    }
}

actual class IPv4 actual constructor(override val bytes: ByteArray) : IP {

    private val text by lazy { Ipaddr.fromByteArray(bytes.toUnsignedOctets()).toNormalizedString() }

    override fun compareTo(other: IP): Int {
        val maxSize = maxOf(bytes.size, other.bytes.size)
        val thisOffset = maxSize - bytes.size
        val otherOffset = maxSize - other.bytes.size
        for (i in 0 until maxSize) {
            val thisByte = if (i >= thisOffset) bytes[i - thisOffset] else 0x00
            val otherByte = if (i >= otherOffset) other.bytes[i - otherOffset] else 0x00
            if (thisByte < otherByte) return -1
            if (thisByte > otherByte) return 1
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

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

actual class IPv6 actual constructor(override val bytes: ByteArray) : IP {

    private val text by lazy {
        IPv6Compressor.compress(Ipaddr.fromByteArray(bytes.toUnsignedOctets()).toNormalizedString())
    }

    override fun compareTo(other: IP): Int {
        val maxSize = maxOf(bytes.size, other.bytes.size)
        val thisOffset = maxSize - bytes.size
        val otherOffset = maxSize - other.bytes.size
        for (i in 0 until maxSize) {
            val thisByte = if (i >= thisOffset) bytes[i - thisOffset] else 0x00
            val otherByte = if (i >= otherOffset) other.bytes[i - otherOffset] else 0x00
            if (thisByte < otherByte) return -1
            if (thisByte > otherByte) return 1
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

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
