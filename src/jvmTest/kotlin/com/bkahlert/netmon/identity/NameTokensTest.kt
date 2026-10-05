package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NameTokensTest {

    @Test
    fun a_brand_prefix_names_vendor_and_kind() = runTest {
        forAll(
            row("LEDVANCE-Sideboard-TV", "Ledvance", Kind.LAMP),
            row("RingIntercom-6e", "Ring", Kind.DOOR_BELL),
            row("Sonoff-MINIR4M", "Sonoff", Kind.SOCKET),
            row("tado-bridge-x", "tado", Kind.HUB),
            row("FYTA-HUB", "FYTA", Kind.HUB),
            row("net-ac-0506", "Midea", Kind.AIR_CONDITIONER),
            row("Nanoleaf-Shapes", "Nanoleaf", Kind.LAMP),
            row("Sonos-OneSL", "Sonos", Kind.SPEAKER),
            row("Philips-HueBridge", "Signify", Kind.HUB),
            row("zhimi-airpurifier-v7", "Xiaomi", Kind.AIR_PURIFIER),
        ) { name, vendor, kind ->
            val result = NameTokens.matches(name).first()

            result.vendor shouldBe vendor
            result.kind shouldBe kind
        }
    }

    @Test
    fun a_product_word_names_only_the_kind() = runTest {
        forAll(
            row("espressif", Kind.CIRCUIT_BOARD),
            row("ESP_1A2B3C", Kind.CIRCUIT_BOARD),
            row("iPhone", Kind.SMARTPHONE),
            row("Rabban's iPad", Kind.TABLET),
            row("macbookproista", Kind.LAPTOP),
            row("pihole-alex", Kind.COMPUTER),
            row("homeassistent", Kind.COMPUTER),
            row("LGwebOSTV", Kind.TELEVISION),
            row("tv6303811c1a46", Kind.TELEVISION),
            row("Indoorcam", Kind.CAMERA),
            row("NPID96FF6", Kind.PRINTER),
            row("Apple-HomePod", Kind.SPEAKER),
        ) { name, kind ->
            val result = NameTokens.matches(name).first()

            result.vendor shouldBe null
            result.kind shouldBe kind
        }
    }

    @Test
    fun a_location_word_like_tv_in_a_plug_name_does_not_make_a_television() {
        val result = NameTokens.matches("LEDVANCE-Sideboard-TV")

        result.map { it.kind } shouldBe listOf(Kind.LAMP)
    }

    @Test
    fun an_unknown_name_matches_nothing() {
        NameTokens.matches("Bellonda").shouldBeEmpty()
    }
}
