package com.bkahlert.netmon.contract

import com.bkahlert.netmon.contract.serialization.JsonFormat
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class IPTest {

    @Test
    fun zero_prefix_addresses() = runTest {
        forAll(
            row("::", ByteArray(16)),
            row("::1", byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
            row("fe80::1", byteArrayOf(-2, -128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
        ) { text, expected ->
            val result = IP.of(text)
            result should {
                it::class shouldBe IPv6::class
                it.bytes.size shouldBe 16
                it.bytes shouldBe expected
            }
        }
    }

    @Test
    fun mixed_mapped_address() {
        val result = IP.of("::ffff:192.168.0.1")
        result should {
            it shouldBe IP.of("192.168.0.1")
            it::class shouldBe IPv4::class
            it.bytes shouldBe byteArrayOf(-64, -88, 0, 1)
        }
    }

    @Test
    fun mapped_cidr() {
        val result = Cidr.parse("::ffff:192.168.0.1/128")
        result should {
            it.ip shouldBe IP.of("192.168.0.1")
            it.mask shouldBe 32
            it.toString() shouldBe "192.168.0.1/32"
            it.filenameString shouldBe "192-168-0-1_32"
        }
    }

    @Test
    fun codec_round_trips() = runTest {
        forAll(
            row("::", "::", "--"),
            row("::1", "::1", "--1"),
            row("fe80::1", "fe80::1", "fe80--1"),
            row("::ffff:192.168.0.1", "192.168.0.1", "192-168-0-1"),
            row("::ffff:c0a8:0001", "192.168.0.1", "192-168-0-1"),
            row("2001:db8::192.168.0.1", "2001:db8::c0a8:1", "2001-db8--c0a8-1"),
            row("2001:0:0:1:0:0:2:3", "2001::1:0:0:2:3", "2001--1-0-0-2-3"),
            row("2001:db8:1:0:2:3:4:5", "2001:db8:1::2:3:4:5", "2001-db8-1--2-3-4-5"),
            row("ffff:8000:abcd:1:2:3:4:ff", "ffff:8000:abcd:1:2:3:4:ff", "ffff-8000-abcd-1-2-3-4-ff"),
        ) { input, expectedText, expectedFilename ->
            val result = IP.of(input)
            result should {
                it.toString() shouldBe expectedText
                it.filenameString shouldBe expectedFilename
            }
            val fromBytes = IP.of(result.bytes)
            val fromText = IP.of(result.toString())
            val json = JsonFormat.encodeToString(IPSerializer, result)
            val fromJson = JsonFormat.decodeFromString(IPSerializer, json)
            fromBytes shouldBe result
            fromText shouldBe result
            json shouldBe "\"$expectedText\""
            fromJson shouldBe result
        }
    }

    @Test
    fun instantiation() = runTest {
        forAll(
            row("192.168.0.1", IP.of("192.168.0.1"), IPv4::class),
            row("::ffff:c0a8:0001", IP.of("::ffff:c0a8:0001"), IPv4::class),
            row("2001:db8::", IP.of("2001:db8::"), IPv6::class),
            row("2001:0db8:0000:0000:0000:0000:0000:0000", IP.of("2001:db8::"), IPv6::class),
        ) { text, expected, expectedType ->
            IP.of(text) should {
                it shouldBe expected
                it::class shouldBe expectedType
            }
        }
    }

    @Test
    fun text() = runTest {
        forAll(
            row(IP.of("192.168.0.1"), "192.168.0.1"),
            row(IP.of("::ffff:c0a8:0001"), "192.168.0.1"),
            row(IP.of("2001:db8::"), "2001:db8::"),
            row(IP.of("2001:0db8:0000:0000:0000:0000:0000:0000"), "2001:db8::"),
        ) { ip, expected ->
            ip.toString() shouldBe expected
        }
    }

    @Test
    fun bytes() = runTest {
        forAll(
            row("192.168.0.1", byteArrayOf(-64, -88, 0, 1)),
            row("::ffff:c0a8:0001", byteArrayOf(-64, -88, 0, 1)),
            row("2001:db8::", byteArrayOf(32, 1, 13, -72, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            row("2001:0db8:0000:0000:0000:0000:0000:0000", byteArrayOf(32, 1, 13, -72, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        ) { ip, expected ->
            IP.of(ip).bytes shouldBe expected
        }
    }

    @Test
    fun compare() {
        IP.of("192.168.0.1") shouldBeEqualComparingTo IP.of("192.168.0.1")
        IP.of("192.168.0.1") shouldBeLessThan IP.of("192.168.0.2")
        IP.of("::ffff:c0a8:0001") shouldBeLessThan IP.of("::ffff:c0a8:0002")
        IP.of("192.168.0.1") shouldBeEqualComparingTo IP.of("::ffff:c0a8:0001")
        IP.of("2001:db8::") shouldBeEqualComparingTo IP.of("2001:0db8:0000:0000:0000:0000:0000:0000")
    }

    @Test
    fun equality() {
        IP.of("192.168.0.1") shouldBeEqual IP.of("192.168.0.1")
        IP.of("192.168.0.1") shouldNotBeEqual IP.of("192.168.0.2")
        IP.of("::ffff:c0a8:0001") shouldNotBeEqual IP.of("::ffff:c0a8:0002")
        IP.of("192.168.0.1") shouldBeEqual IP.of("::ffff:c0a8:0001")
        IP.of("2001:db8::") shouldBeEqual IP.of("2001:0db8:0000:0000:0000:0000:0000:0000")
    }

    @Test
    fun filename_string() = runTest {
        forAll(
            row("192.168.0.1", "192-168-0-1"),
            row("::ffff:c0a8:0001", "192-168-0-1"),
            row("2001:db8::", "2001-db8--"),
            row("2001:0db8:0000:0000:0000:0000:0000:0000", "2001-db8--"),
        ) { ip, expected ->
            IP.of(ip).filenameString shouldBe expected
        }
    }

    @Test
    fun to_json() = runTest {
        forAll(
            row(IP.of("192.168.0.1")),
            row(IP.of("::ffff:c0a8:0001")),
            row(IP.of("192.168.0.1")),
            row(IP.of("2001:db8::")),
        ) { ip ->
            JsonFormat.encodeToString(ip) shouldBe ip.let { "\"$it\"" }
            JsonFormat.encodeToString(IPSerializer, ip) shouldBe ip.let { "\"$it\"" }
        }
    }

    @Test
    fun from_json() = runTest {
        forAll(
            row(IP.of("192.168.0.1")),
            row(IP.of("::ffff:c0a8:0001")),
            row(IP.of("192.168.0.1")),
            row(IP.of("2001:db8::")),
        ) { ip ->
            JsonFormat.decodeFromString<IP>(ip.let { "\"$it\"" }) shouldBe ip
            JsonFormat.decodeFromString(IPSerializer, ip.let { "\"$it\"" }) shouldBe ip
        }
    }
}
