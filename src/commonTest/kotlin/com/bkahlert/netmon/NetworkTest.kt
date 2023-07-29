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
            row(Network.IPv4, Network.IPv4String),
            row(Network.IPv6, Network.IPv6String),
        ) { network, expected ->
            network.toString() shouldBe expected
        }
    }
}

val Network.Companion.IPv4: Network get() = Network("foo", "en0", Cidr("10.0.0.1/24"))
val Network.Companion.IPv4String: String get() = "foo:en0(10.0.0.1/24)"
val Network.Companion.IPv6: Network get() = Network("bar.baz", "wifi1", Cidr("::ffff:0a00:0001/104"))
val Network.Companion.IPv6String: String get() = "bar.baz:wifi1(::ffff:0a00:0001/104)"
