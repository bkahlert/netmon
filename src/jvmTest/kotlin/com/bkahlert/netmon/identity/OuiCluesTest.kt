package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.invoke
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OuiCluesTest {

    @Test
    fun the_raw_vendor_becomes_a_normalized_oui_vendor_clue_with_the_vendors_default_kind() = runTest {
        forAll(
            row("Nintendo", "80:00:00:00:00:0d", "Nintendo", Kind.GAMING_DEVICE),
            row("Ring", "64:00:00:00:00:04", "Ring", Kind.DOOR_BELL),
            row("Tuya Smart", "a8:00:00:00:00:06", "Tuya", Kind.SOCKET),
            row("Espressif", "34:00:00:00:00:05", "Espressif", Kind.CIRCUIT_BOARD),
            row("Raspberry Pi Foundation", "b8:00:00:00:00:01", "Raspberry Pi", Kind.CIRCUIT_BOARD),
            row("Amazon Technologies", "58:00:00:00:00:06", "Amazon", Kind.SPEAKER),
            row("GD Midea Air-Conditioning Equipment", "bc:00:00:00:00:07", "Midea", Kind.AIR_CONDITIONER),
            row("Philips Lighting BV", "ec:00:00:00:00:08", "Signify", Kind.HUB),
            row("HP", "84:00:00:00:00:09", "HP", Kind.PRINTER),
            row("LG Electronics", "00:00:00:00:00:0a", "LG", Kind.TELEVISION),
            row("AVM Audiovisuelles Marketing und Computersysteme", "60:00:00:00:00:0b", "AVM", Kind.ROUTER),
        ) { raw, mac, vendor, kind ->
            val result = OuiClues().clues(Host(vendor = raw, mac = mac))

            result shouldContainExactly listOf(Clue.Vendor(vendor, Source.OUI), Clue.DeviceKind(kind, Source.OUI))
        }
    }

    @Test
    fun a_vendor_without_a_default_kind_yields_only_the_vendor() {
        val result = OuiClues().clues(Host(vendor = "Ugreen Group Limited", mac = "6c:00:00:00:00:0c"))

        result shouldContainExactly listOf(Clue.Vendor("Ugreen", Source.OUI))
    }

    @Test
    fun a_private_mac_yields_nothing_even_with_a_vendor() {
        OuiClues().clues(Host(vendor = "QEMU virtual NIC", mac = "52:54:00:12:34:56")).shouldBeEmpty()
    }

    @Test
    fun no_vendor_yields_nothing() {
        OuiClues().clues(Host(vendor = null, mac = "d4:00:00:00:00:02")).shouldBeEmpty()
    }
}
