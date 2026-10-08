@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.support.bytes

/** Returns a subarray of this byte array having leading zeros removed. */
public fun ByteArray.trimStart(): ByteArray = trimStart { it == 0x00.toByte() }

/** Returns a subarray having leading bytes from the [bytes] array removed. */
public fun ByteArray.trimStart(vararg bytes: Byte): ByteArray = trimStart { it in bytes }

/** Returns a subarray of this byte array having leading bytes matching the [predicate] removed. */
public inline fun ByteArray.trimStart(predicate: (Byte) -> Boolean): ByteArray {
    for (index in this.indices)
        if (!predicate(this[index]))
            return sliceArray(index.rangeUntil(size))

    return byteArrayOf()
}

/** Returns a [ByteArray] representing the numeric value of this [ByteArray] incremented by one. */
public operator fun ByteArray.inc(): ByteArray {
    val result = copyOf()
    var carry = 1

    // Loop from the least significant byte back to the most significant byte
    for (i in size - 1 downTo 0) {
        val value = get(i).toInt().and(0xff) + carry
        if (value > 0xff) {  // overflow
            result[i] = 0x00
            carry = 1
        } else {
            result[i] = value.toByte()
            carry = 0
            break
        }
    }

    return if (carry == 0) result.trimStart()
    else ByteArray(size + 1) { index -> if (index == 0) 1 else result[index - 1] }
}

/** Returns a [ByteArray] representing the numeric value of this [ByteArray] decremented by one. */
public operator fun ByteArray.dec(): ByteArray {
    if (all { it == 0x00.toByte() }) throw ArithmeticException("Cannot decrement zero UByteArray.")

    val result = copyOf()
    var carry = 1

    // Loop from the least significant byte back to the most significant byte
    for (i in size - 1 downTo 0) {
        val value = get(i).toInt().and(0xff) - carry
        if (value < 0x00) {  // underflow
            result[i] = 0xff.toByte()
            carry = 1
        } else {
            result[i] = value.toByte()
            carry = 0
        }
    }

    return result.trimStart()
}
