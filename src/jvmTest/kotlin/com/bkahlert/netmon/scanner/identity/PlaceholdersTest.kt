package com.bkahlert.netmon.scanner.identity

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PlaceholdersTest {

    @Test
    fun drops_router_defaults_mac_like_and_uuid_names() = runTest {
        forAll(
            row("PC-02-AA-BB-CC-00-08"),
            row("PC-192-0-2-12"),
            row("PC---0000-0000-0000-0001"),
            row("none"),
            row("EC000000001B"),
            row("ec0000000008.local."),
            row("00000000-0000-4000-8000-000000000002.local."),
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
    fun drops_eui_64_host_names_of_matter_devices() = runTest {
        forAll(
            row("0200000000AB0000"),
            row("0200000000ab0000.local."),
            row("0A1B2C3D4E5F6071"),
        ) { name ->
            Placeholders.clean(name) shouldBe null
        }
    }

    @Test
    fun drops_uuid_host_names_without_dashes_like_home_assistant_s() = runTest {
        forAll(
            row("00000000000040008000000000000002"),
            row("0a0000000000400080000000000000ff.local."),
            row("0A0000000000400080000000000000FF"),
        ) { name ->
            Placeholders.clean(name) shouldBe null
        }
    }

    @Test
    fun keeps_real_names_without_the_local_suffix_or_trailing_dot() = runTest {
        forAll(
            row("LEDVANCE-Hallway-TV", "LEDVANCE-Hallway-TV"),
            row("Sam-2.local.", "Sam-2"),
            row("fritz.box.", "fritz.box"),
            row("Fire TV Stick 4K", "Fire TV Stick 4K"),
            row("tado-IB0000000001.local.", "tado-IB0000000001"),
            row("0200000000AB00", "0200000000AB00"),
            row("0000000000004000800000000000002", "0000000000004000800000000000002"),
            row("000000000000400080000000000000002", "000000000000400080000000000000002"),
            row("cafe-babe-deadbeef", "cafe-babe-deadbeef"),
            row("Deadbeef-Cafe", "Deadbeef-Cafe"),
        ) { name, expected ->
            Placeholders.clean(name) shouldBe expected
        }
    }
}
