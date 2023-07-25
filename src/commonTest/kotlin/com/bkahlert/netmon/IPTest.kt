package com.bkahlert.netmon

import io.kotest.common.runBlocking
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.math.BigInteger

class IPTest {

    @Test
    fun int_value() = runBlocking {
        forAll(
            row("10.0.0.1", "167772161"),
            row("10.0.0.2", "167772162"),
            row("::ffff:0a00:0001", "167772161"),
            row("::ffff:0a00:0002", "167772162"),
        ) { ip, expected ->
            IP(ip).intValue shouldBe BigInteger(expected)
        }
    }

    @Test
    fun filename_string() = runBlocking {
        forAll(
            row("10.0.0.1", "10-0-0-1"),
            row("::ffff:0a00:0001", "--ffff-0a00-0001"),
        ) { ip, expected ->
            IP(ip).filenameString shouldBe expected
        }
    }
}
