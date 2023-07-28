package com.bkahlert.netmon

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NetworkTest {

    @Test
    fun to_string() = runTest {
        forAll(
            row(Network("foo", "en0", Cidr("10.0.0.1/24")), "foo:en0(10.0.0.1/24)"),
            row(Network("bar.baz", "wifi1", Cidr("::ffff:0a00:0001/104")), "bar.baz:wifi1(::ffff:0a00:0001/104)"),
        ) { network, expected ->
            network.toString() shouldBe expected
        }
    }
}
