package com.bkahlert.netmon

import kotlinx.serialization.Serializable

@Serializable(with = IPSerializer::class)
actual sealed interface IP : Comparable<IP> {
    actual val bytes: ByteArray

    actual companion object {

        actual fun of(bytes: ByteArray): IP = when (bytes.size) {
            4 -> IPv4(bytes)
            16 -> IPv6(bytes)
            else -> throw IllegalArgumentException("Invalid IP address length: ${bytes.size}")
        }

        actual fun of(text: String): IP = of(when {
            text.contains('.') -> {
                val sanitized = text
                    .dropWhile { it !in "0123456789." }
                    .dropLastWhile { it !in "0123456789." }
                sanitized
                    .split('.')
                    .map { it.toUByte() }
                    .toUByteArray()
                    .toByteArray()
            }

            text.contains(':') -> {
                val sanitized = text
                    .dropWhile { it !in "0123456789abcdefABCDEF:" }
                    .dropLastWhile { it !in "0123456789abcdefABCDEF:" }

                fun uBytesOf(list: List<String>): UByteArray = list
                    .flatMap { it.padStart(4, '0').chunked(2) { it.toString().toUByte(16) } }
                    .toUByteArray()

                val (left, right) = sanitized
                    .split("::")
                    .let { it[0] to it.getOrNull(1) }
                val leftBytes = uBytesOf(left.split(':'))
                val rightBytes = uBytesOf(right?.split(':').orEmpty())

                val result = UByteArray(16)
                leftBytes.copyInto(result, 0, 0, leftBytes.size)
                rightBytes.copyInto(result, 16 - rightBytes.size, 0, rightBytes.size)

                val trimmed = result.dropWhile { it == 0x00.toUByte() }
                    .toUByteArray()
                    .takeUnless { it.isEmpty() }
                    ?: ubyteArrayOf(0x00.toUByte())

                // IPv4 mapped IPv6 addresses
                if (trimmed.size == 6 && trimmed[0] == UByte.MAX_VALUE && trimmed[1] == UByte.MAX_VALUE) trimmed.sliceArray(2..5).toByteArray()
                else trimmed.toByteArray()
            }

            else -> error("Invalid IP address: $text")
        })
    }
}

actual class IPv4 actual constructor(override val bytes: ByteArray) : IP {

    private val text by lazy { bytes.joinToString(".") }

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
}

actual class IPv6 actual constructor(override val bytes: ByteArray) : IP {

    private val text by lazy {
        IPv6Compressor.compress(buildString(39) {
            for (i in 0 until 8) {
                append((((bytes[i * 2].toInt() and 0xff) shl Byte.SIZE_BITS) or (bytes[i * 2 + 1].toInt() and 0xff)).toString(16))
                if (i < 7) append(":")
            }
        })
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
}
