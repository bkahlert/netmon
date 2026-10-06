package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AppleCodesTest {

    @Test
    fun a_known_code_is_accepted_for_an_apple_private_or_unknown_mac() = runTest {
        forAll(
            row("iPad8,3", "Apple", "02:aa:bb:cc:00:19", true),
            row("Mac14,13", null, "02:aa:bb:cc:00:1a", true),
            row("AirPort5", null, "d4:00:00:00:00:02", true),
            row("AirPort4", "Raspberry Pi", "b8:00:00:00:00:01", false),
            row("MacPro7,1@ECOLOR=226,226,226", "Ugreen", "6c:00:00:00:00:0c", false),
            row("AppleTV3,1", "Amazon", "ec:00:00:00:00:03", false),
            row("Hoverboard1,1", "Apple", "02:aa:bb:cc:00:19", false),
        ) { code, vendor, mac, expected ->
            codes.accepts(code, ouiVendor = vendor, mac = mac, linuxHost = false) shouldBe expected
        }
    }

    @Test
    fun a_linux_host_is_never_believed() {
        codes.accepts("AirPort5", ouiVendor = null, mac = "d4:00:00:00:00:02", linuxHost = true) shouldBe false
    }

    @Test
    fun the_finder_colour_suffix_is_stripped() {
        codes.normalize("Mac15,4@ECOLOR=4") shouldBe "Mac15,4"
        codes.kindOf("Mac14,13@ECOLOR=4") shouldBe Kind.LAPTOP
    }

    @Test
    fun a_table_code_that_is_not_apple_shaped_is_not_an_apple_code() = runTest {
        forAll(row("One SL"), row("FireTV"), row("Speaker"), row("FireTVStick4K")) { code ->
            codes.isKnown(code) shouldBe false
        }
        codes.isKnown("AirPort4") shouldBe true
        codes.isKnown("iPad8,3") shouldBe true
    }

    @Test
    fun the_kind_follows_its_explicit_classification() = runTest {
        forAll(
            row("iPad8,3", Kind.TABLET),
            row("iPhone14,2", Kind.SMARTPHONE),
            row("Mac14,13", Kind.LAPTOP),
            row("Mac14,8", Kind.COMPUTER),
            row("AppleTV3,1", Kind.SET_TOP_BOX),
            row("AudioAccessory5,1", Kind.SPEAKER),
            row("Watch6,1", Kind.SMART_WATCH),
            row("AirPort4", Kind.ROUTER),
            row("Hoverboard1,1", null),
        ) { code, expected ->
            codes.kindOf(code) shouldBe expected
        }
    }

    @Test
    fun classification_uses_explicit_kind_without_a_symbol() {
        val classifier = AppleCodes(ModelCatalog(mapOf("Mac14,13" to Kind.LAPTOP)))

        classifier.kindOf("Mac14,13") shouldBe Kind.LAPTOP
    }

    @Test
    fun new_models_have_no_implicit_kind() {
        val catalog = ModelCatalog(mapOf("Mac99,1" to null))

        catalog.contains("Mac99,1") shouldBe true
        AppleCodes(catalog).kindOf("Mac99,1") shouldBe null
    }
}

private val codes = AppleCodes(
    ModelCatalog(
        mapOf(
            "iPad8,3" to Kind.TABLET,
            "iPhone14,2" to Kind.SMARTPHONE,
            "Mac14,13" to Kind.LAPTOP,
            "Mac14,8" to Kind.COMPUTER,
            "Mac15,4" to Kind.COMPUTER,
            "MacPro7,1" to Kind.COMPUTER,
            "AppleTV3,1" to Kind.SET_TOP_BOX,
            "AudioAccessory5,1" to Kind.SPEAKER,
            "Watch6,1" to Kind.SMART_WATCH,
            "AirPort4" to Kind.ROUTER,
            "AirPort5" to Kind.ROUTER,
            "One SL" to Kind.SPEAKER,
            "FireTV" to null,
            "Speaker" to Kind.SPEAKER,
            "FireTVStick4K" to null,
        ),
    ),
)
