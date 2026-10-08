package com.bkahlert.netmon.scanner.identity

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MacAddressesTest {

    @Test
    fun a_locally_administered_address_is_private() = runTest {
        forAll(
            row("02:aa:bb:cc:00:16", true),
            row("02:aa:bb:cc:00:17", true),
            row("02:aa:bb:cc:00:18", true),
            row("02:42:c6:33:64:0b", true),
            row("52:54:00:12:34:56", true),
            row("b8:00:00:00:00:01", false),
            row("A8:00:00:00:00:06", false),
        ) { mac, expected ->
            MacAddresses.isPrivate(mac) shouldBe expected
        }
    }
}
