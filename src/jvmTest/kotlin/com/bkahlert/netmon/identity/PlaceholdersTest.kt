package com.bkahlert.netmon.identity

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PlaceholdersTest {

    @Test
    fun drops_router_defaults_mac_like_and_uuid_names() = runTest {
        forAll(
            row("PC-80-D2-E5-6B-AD-08"),
            row("PC-192-168-16-12"),
            row("PC---102f-1aa7-7cf9-5ddd"),
            row("none"),
            row("EC8AC43F4FBA"),
            row("ecb5faae8386.local."),
            row("b8be523d-d018-42d2-9d62-f85631e8e835.local."),
            row("android-123456789abcdef"),
            row("espressif"),
            row("ESP_1A2B3C"),
            row(""),
            row("   "),
        ) { name ->
            Placeholders.clean(name) shouldBe null
        }
    }

    @Test
    fun keeps_real_names_without_the_local_suffix_or_trailing_dot() = runTest {
        forAll(
            row("LEDVANCE-Sideboard-TV", "LEDVANCE-Sideboard-TV"),
            row("Paul-2.local.", "Paul-2"),
            row("fritz.box.", "fritz.box"),
            row("Fire TV Stick 4K", "Fire TV Stick 4K"),
            row("tado-IB1875863552.local.", "tado-IB1875863552"),
        ) { name, expected ->
            Placeholders.clean(name) shouldBe expected
        }
    }
}
