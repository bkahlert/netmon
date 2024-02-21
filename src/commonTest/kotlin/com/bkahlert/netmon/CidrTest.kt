package com.bkahlert.netmon

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CidrTest {

    @Test
    fun to_string() = runTest {
        forAll(
            row("10.0.0.1/24"),
            row("::ffff:0a00:0001/104"),
        ) { value ->
            Cidr(value).toString() shouldBe value
        }
    }

    @Test
    fun filename_string() = runTest {
        forAll(
            row("10.0.0.1/24", "10-0-0-1_24"),
            row("2001:0db8:0000:0000:0000:0000:0000:0000/104", "2001-db8--_104"),
        ) { value, expected ->
            Cidr(value).filenameString shouldBe expected
        }
    }
}
