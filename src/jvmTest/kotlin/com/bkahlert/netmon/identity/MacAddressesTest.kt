package com.bkahlert.netmon.identity

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MacAddressesTest {

    @Test
    fun a_locally_administered_address_is_private() = runTest {
        forAll(
            row("de:c8:ff:43:fc:54", true),
            row("be:3b:a1:cf:7c:bf", true),
            row("3a:ef:e4:bc:6f:34", true),
            row("02:42:c0:a8:10:0b", true),
            row("52:54:00:03:c3:31", true),
            row("b8:27:eb:66:2e:c2", false),
            row("A8:80:55:37:E5:C6", false),
        ) { mac, expected ->
            MacAddresses.isPrivate(mac) shouldBe expected
        }
    }
}
