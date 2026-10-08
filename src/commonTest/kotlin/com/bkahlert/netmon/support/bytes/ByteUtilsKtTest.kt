package com.bkahlert.netmon.support.bytes

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ByteUtilsKtTest {

    @Test
    fun trim_start() = runTest {
        forAll(
            row(byteArrayOf(), byteArrayOf()),
            row(byteArrayOf(0x00), byteArrayOf()),
            row(byteArrayOf(0x00, 0x00), byteArrayOf()),
            row(byteArrayOf(0x00, 0x01), byteArrayOf(0x01)),
            row(byteArrayOf(0x01, 0x00), byteArrayOf(0x01, 0x00)),
        ) { bytes, expected ->
            bytes.trimStart() shouldBe expected
            bytes.trimStart(0x00) shouldBe expected
            bytes.trimStart { it == 0x00.toByte() } shouldBe expected
        }
    }

    @Test
    fun inc() = runTest {
        forAll(
            row(byteArrayOf(), byteArrayOf(0x01)),
            row(byteArrayOf(0x00), byteArrayOf(0x01)),
            row(byteArrayOf(0x00, 0x00), byteArrayOf(0x01)),
            row(byteArrayOf(0x00, 0xff.toByte()), byteArrayOf(0x01, 0x00)),
            row(byteArrayOf(0xff.toByte(), 0xff.toByte()), byteArrayOf(0x01, 0x00, 0x00)),
        ) { bytes, expected ->
            bytes.inc() shouldBe expected
        }
    }

    @Test
    fun dec() = runTest {
        forAll(
            row(byteArrayOf(0x01), byteArrayOf()),
            row(byteArrayOf(0x00, 0x01), byteArrayOf()),
            row(byteArrayOf(0x01, 0x00), byteArrayOf(0xff.toByte())),
            row(byteArrayOf(0x01, 0x00, 0x00), byteArrayOf(0xff.toByte(), 0xff.toByte())),
        ) { bytes, expected ->
            bytes.dec() shouldBe expected
        }
    }

    @Test
    fun dec_underflow() = runTest {
        shouldThrow<ArithmeticException> { byteArrayOf().dec() }
        shouldThrow<ArithmeticException> { byteArrayOf(0x00).dec() }
    }
}
