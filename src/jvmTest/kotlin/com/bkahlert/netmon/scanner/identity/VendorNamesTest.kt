package com.bkahlert.netmon.scanner.identity

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class VendorNamesTest {

    @Test
    fun aliases_the_long_names_of_the_prefix_table() = runTest {
        forAll(
            row("Raspberry Pi Foundation", "Raspberry Pi"),
            row("Raspberry Pi Trading", "Raspberry Pi"),
            row("Raspberry Pi (Trading)", "Raspberry Pi"),
            row("Beijing Xiaomi Mobile Software", "Xiaomi"),
            row("GD Midea Air-Conditioning Equipment", "Midea"),
            row("AVM Audiovisuelles Marketing und Computersysteme", "AVM"),
            row("FRITZ! GmbH", "AVM"),
            row("Philips Lighting BV", "Signify"),
            row("Smart Innovation", "eufy"),
            row("Amazon Technologies", "Amazon"),
            row("Tuya Smart", "Tuya"),
            row("Apple Inc.", "Apple"),
            row("Ugreen Group Limited", "Ugreen"),
            row("LG Electronics", "LG"),
            row("Sonos, Inc.", "Sonos"),
            row("Hewlett Packard", "HP"),
            row("QEMU virtual NIC", "QEMU"),
        ) { raw, expected ->
            VendorNames.normalize(raw) shouldBe expected
        }
    }

    @Test
    fun strips_legal_suffixes_from_names_without_an_alias() = runTest {
        forAll(
            row("Acme Technologies Co., Ltd.", "Acme"),
            row("Widgets GmbH", "Widgets"),
            row("Signify", "Signify"),
            row("HP", "HP"),
            row("Nanoleaf", "Nanoleaf"),
        ) { raw, expected ->
            VendorNames.normalize(raw) shouldBe expected
        }
    }
}
