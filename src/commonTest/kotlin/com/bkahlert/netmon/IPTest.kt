package com.bkahlert.netmon

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.ktor.http.quote
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class IPTest {

    @Test
    fun instantiation() = runTest {
        forAll(
            row("10.0.0.1", IP("10.0.0.1")),
            row("::ffff:0a00:0001", IP("::ffff:0a00:0001")),
            row("10.0.0.1", IP("10.0.0.1")),
            row("2001:db8::", IP("2001:db8::")),
        ) { value, expected ->
            IP(value) shouldBe expected
        }
    }

    @Test
    fun value() = runTest {
        forAll(
            row(IP("10.0.0.1"), "10.0.0.1"),
            row(IP("::ffff:0a00:0001"), "::ffff:0a00:0001"),
            row(IP("10.0.0.1"), "10.0.0.1"),
            row(IP("2001:db8::"), "2001:db8::"),
        ) { ip, expected ->
            ip.value shouldBe expected
        }
    }

    @Test
    fun bytes() = runTest {
        forAll(
            row("10.0.0.1", ubyteArrayOf(10u, 0u, 0u, 1u)),
            row("::ffff:0a00:0001", ubyteArrayOf(10u, 0u, 0u, 1u)),
            row("2001:db8::", ubyteArrayOf(32u, 1u, 13u, 184u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
            row("2001:0db8:0000:0000:0000:0000:0000:0000", ubyteArrayOf(32u, 1u, 13u, 184u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u)),
        ) { ip, expected ->
            IP(ip).bytes shouldBe expected
        }
    }

    @Test
    fun compare() {
        IP("10.0.0.1") shouldBeEqualComparingTo IP("10.0.0.1")
        IP("10.0.0.1") shouldBeLessThan IP("10.0.0.2")
        IP("::ffff:0a00:0001") shouldBeLessThan IP("::ffff:0a00:0002")
        IP("10.0.0.1") shouldBeEqualComparingTo IP("::ffff:0a00:0001")
        IP("2001:db8::") shouldBeEqualComparingTo IP("2001:0db8:0000:0000:0000:0000:0000:0000")
    }

    @Test
    fun equality() {
        IP("10.0.0.1") shouldBeEqual IP("10.0.0.1")
        IP("10.0.0.1") shouldNotBeEqual IP("10.0.0.2")
        IP("::ffff:0a00:0001") shouldNotBeEqual IP("::ffff:0a00:0002")
        IP("10.0.0.1") shouldBeEqual IP("::ffff:0a00:0001")
        IP("2001:db8::") shouldBeEqual IP("2001:0db8:0000:0000:0000:0000:0000:0000")
    }

    @Test
    fun filename_string() = runTest {
        forAll(
            row("10.0.0.1", "10-0-0-1"),
            row("::ffff:0a00:0001", "--ffff-0a00-0001"),
        ) { ip, expected ->
            IP(ip).filenameString shouldBe expected
        }
    }

    @Test
    fun to_json() = runTest {
        forAll(
            row(IP("10.0.0.1")),
            row(IP("::ffff:0a00:0001")),
            row(IP("10.0.0.1")),
            row(IP("2001:db8::")),
        ) { ip ->
            JsonFormat.encodeToString(ip) shouldBe ip.value.quote()
            JsonFormat.encodeToString(IPSerializer, ip) shouldBe ip.value.quote()
        }
    }

    @Test
    fun from_json() = runTest {
        forAll(
            row(IP("10.0.0.1")),
            row(IP("::ffff:0a00:0001")),
            row(IP("10.0.0.1")),
            row(IP("2001:db8::")),
        ) { ip ->
            JsonFormat.decodeFromString<IP>(ip.value.quote()) shouldBe ip
            JsonFormat.decodeFromString(IPSerializer, ip.value.quote()) shouldBe ip
        }
    }
}
