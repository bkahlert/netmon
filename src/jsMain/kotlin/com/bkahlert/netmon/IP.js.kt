package com.bkahlert.netmon

import kotlinx.serialization.Serializable

@Serializable(with = IPSerializer::class)
actual class IP actual constructor(actual val value: String) : Comparable<IP> {

    actual val bytes: UByteArray by lazy {
        when {
            value.contains('.') -> {
                val sanitized = value
                    .dropWhile { it !in "0123456789." }
                    .dropLastWhile { it !in "0123456789." }
                sanitized
                    .split('.')
                    .map { it.toUByte() }
                    .toUByteArray()
            }

            value.contains(':') -> {
                val sanitized = value
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

                val trimmed = result.dropWhile { it == UByte.MIN_VALUE }
                    .toUByteArray()
                    .takeUnless { it.isEmpty() }
                    ?: ubyteArrayOf(UByte.MIN_VALUE)

                // IPv4 mapped IPv6 addresses
                if (trimmed.size == 6 && trimmed[0] == UByte.MAX_VALUE && trimmed[1] == UByte.MAX_VALUE) trimmed.sliceArray(2..5)
                else trimmed
            }

            else -> error("Invalid IP address: $value")
        }
    }

    override fun compareTo(other: IP): Int {
        val maxSize = maxOf(bytes.size, other.bytes.size)
        val thisOffset = maxSize - bytes.size
        val otherOffset = maxSize - other.bytes.size
        for (i in 0 until maxSize) {
            val thisByte = if (i >= thisOffset) bytes[i - thisOffset] else 0u
            val otherByte = if (i >= otherOffset) other.bytes[i - otherOffset] else 0u
            if (thisByte < otherByte) return -1
            if (thisByte > otherByte) return 1
        }
        return 0
    }


    override fun toString(): String = value
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as IP

        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}
