package com.bkahlert.netmon.contract

import com.bkahlert.netmon.contract.serialization.JsonFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class CidrTest {

    @Test
    fun instantiation() = runTest {
        forAll(
            row(IP.of("192.168.0.1"), 24),
            row(IP.of("2001:db8::"), 32),
        ) { ip, mask ->
            Cidr(ip, mask) should {
                it.ip shouldBe ip
                it.mask shouldBe mask
            }
        }

        shouldThrow<IllegalArgumentException> { Cidr(IP.of("192.168.0.1"), -1) }
        shouldThrow<IllegalArgumentException> { Cidr(IP.of("192.168.0.1"), 33) }
        shouldThrow<IllegalArgumentException> { Cidr(IP.of("2001:db8::"), -1) }
        shouldThrow<IllegalArgumentException> { Cidr(IP.of("2001:db8::"), 129) }
    }

    @Test
    fun parse() = runTest {
        forAll(
            row("192.168.0.1/24", Cidr(IP.of("192.168.0.1"), 24)),
            row("::ffff:c0a8:0001/120", Cidr(IP.of("192.168.0.1"), 24)),
            row("2001:db8::/32", Cidr(IP.of("2001:db8::"), 32)),
            row("2001:0db8:0000:0000:0000:0000:0000:0000/32", Cidr(IP.of("2001:db8::"), 32)),
        ) { text, expected ->
            Cidr.parse(text) shouldBe expected
        }

        forAll(
            row("24"),
            row("192.168.0.1/20/24"),
        ) { text ->
            shouldThrow<IllegalArgumentException> { Cidr.parse(text) }
        }
    }

    @Test
    fun equality() = runTest {
        Cidr(IP.of("192.168.0.1"), 24) should {
            it shouldBe Cidr(IP.of("192.168.0.1"), 24)
            it shouldNotBe Cidr(IP.of("192.168.0.2"), 24)
            it shouldNotBe Cidr(IP.of("192.168.0.1"), 25)
        }
    }

    @Test
    fun to_string() = runTest {
        forAll(
            row(Cidr(IP.of("192.168.0.1"), 24), "192.168.0.1/24"),
            row(Cidr(IP.of("2001:db8::"), 32), "2001:db8::/32"),
        ) { cidr, expected ->
            cidr.toString() shouldBe expected
        }
    }

    @Test
    fun filename_string() = runTest {
        forAll(
            row(Cidr(IP.of("192.168.0.1"), 24), "192-168-0-1_24"),
            row(Cidr(IP.of("2001:db8::"), 32), "2001-db8--_32"),
        ) { cidr, expected ->
            cidr.filenameString shouldBe expected
        }
    }

    @Test
    fun serialization() = runTest {
        JsonFormat.encodeToString(Cidr(IP.of("192.168.0.1"), 24)) shouldBe "\"192.168.0.1/24\""
    }

    @Test
    fun deserialization() = runTest {
        JsonFormat.decodeFromString<Cidr>("\"192.168.0.1/24\"") shouldBe Cidr(IP.of("192.168.0.1"), 24)
        JsonFormat.decodeFromString<Cidr>("\"::ffff:c0a8:0001/120\"") shouldBe Cidr(IP.of("192.168.0.1"), 24)
    }
}
