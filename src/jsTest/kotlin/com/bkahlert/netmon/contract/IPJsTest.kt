package com.bkahlert.netmon.contract

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IPJsTest {

    @Test
    fun leading_zero_octets_are_decimal() = runTest {
        forAll(
            row("010.008.000.001", "10.8.0.1"),
            row("00192.00168.00000.00001", "192.168.0.1"),
        ) { input, expected ->
            val result = IP.of(input)
            result should {
                it.toString() shouldBe expected
                it.bytes.size shouldBe 4
            }
        }
    }

    @Test
    fun supported_surrounding_characters() = runTest {
        forAll(
            row(" \t192.168.0.1\n", "192.168.0.1"),
            row("/192.168.0.1", "192.168.0.1"),
            row("[192.168.0.1]", "192.168.0.1"),
            row("\"192.168.0.1\"", "192.168.0.1"),
            row(" \tfe80::1\n", "fe80::1"),
            row("/fe80::1", "fe80::1"),
            row("[fe80::1]", "fe80::1"),
            row("\"fe80::1\"", "fe80::1"),
        ) { input, expected ->
            val result = IP.of(input)
            result.toString() shouldBe expected
        }
    }

    @Test
    fun malformed_ipv6_is_rejected() = runTest {
        forAll(
            row("2001::db8::1"),
            row("2001:db8:0:0:0:0:0:0:1"),
            row("2001:db8:1"),
            row("2001:db8:0:0:0:0:0:1::"),
            row("2001:db8:10000::1"),
            row("fe80::1%eth0"),
        ) { input ->
            shouldThrowAny { IP.of(input) }
        }
    }

    @Test
    fun non_decimal_or_non_four_part_ipv4_is_rejected() = runTest {
        forAll(
            row("256.168.0.1"),
            row("192.168.0.256"),
            row("192.168.0.1.2"),
            row("127.1"),
            row("127.0.1"),
            row("localhost"),
            row("example.com"),
            row("2130706433"),
            row("0x7f000001"),
            row("0x7f.0.0.1"),
            row("127.0x0.0.1"),
            row("0o177.0.0.1"),
            row("127.-1.0.1"),
        ) { input ->
            shouldThrowAny { IP.of(input) }
        }
    }

    @Test
    fun mixed_octets_are_decimal() = runTest {
        forAll(
            row("::ffff:010.008.000.001", "10.8.0.1", 4),
            row("[::ffff:192.168.0.1]", "192.168.0.1", 4),
            row("2001:db8::010.008.000.001", "2001:db8::a08:1", 16),
        ) { input, expected, expectedSize ->
            val result = IP.of(input)
            result should {
                it.toString() shouldBe expected
                it.bytes.size shouldBe expectedSize
            }
        }
    }

    @Test
    fun malformed_mixed_octets_are_rejected() = runTest {
        forAll(
            row("::ffff:256.168.0.1"),
            row("::ffff:0x7f.0.0.1"),
            row("::ffff:127.1"),
            row("::ffff:127.0.1"),
            row("::ffff:127.0.0.1.2"),
        ) { input ->
            shouldThrowAny { IP.of(input) }
        }
    }
}
