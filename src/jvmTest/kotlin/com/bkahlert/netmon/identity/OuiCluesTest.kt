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
            row("Nintendo", "80:d2:e5:6b:ad:08", "Nintendo", Kind.GAMING_DEVICE),
            row("Ring", "64:9a:63:d0:4d:6e", "Ring", Kind.DOOR_BELL),
            row("Tuya Smart", "a8:80:55:37:e5:c6", "Tuya", Kind.SOCKET),
            row("Espressif", "34:b7:da:e8:10:34", "Espressif", Kind.CIRCUIT_BOARD),
            row("Raspberry Pi Foundation", "b8:27:eb:66:2e:c2", "Raspberry Pi", Kind.CIRCUIT_BOARD),
            row("Amazon Technologies", "58:a8:e8:4f:ce:27", "Amazon", Kind.SPEAKER),
            row("GD Midea Air-Conditioning Equipment", "bc:89:f8:91:f4:32", "Midea", Kind.AIR_CONDITIONER),
            row("Philips Lighting BV", "ec:b5:fa:ae:83:86", "Signify", Kind.HUB),
            row("HP", "84:69:93:d9:6f:f6", "HP", Kind.PRINTER),
            row("LG Electronics", "00:a1:59:1b:dc:28", "LG", Kind.TELEVISION),
            row("AVM Audiovisuelles Marketing und Computersysteme", "60:b5:8d:32:39:d9", "AVM", Kind.ROUTER),
        ) { raw, mac, vendor, kind ->
            val result = OuiClues().clues(Host(vendor = raw, mac = mac))

            result shouldContainExactly listOf(Clue.Vendor(vendor, Source.OUI), Clue.DeviceKind(kind, Source.OUI))
        }
    }

    @Test
    fun a_vendor_without_a_default_kind_yields_only_the_vendor() {
        val result = OuiClues().clues(Host(vendor = "Ugreen Group Limited", mac = "6c:1f:f7:a6:09:42"))

        result shouldContainExactly listOf(Clue.Vendor("Ugreen", Source.OUI))
    }

    @Test
    fun a_private_mac_yields_nothing_even_with_a_vendor() {
        OuiClues().clues(Host(vendor = "QEMU virtual NIC", mac = "52:54:00:03:c3:31")).shouldBeEmpty()
    }

    @Test
    fun no_vendor_yields_nothing() {
        OuiClues().clues(Host(vendor = null, mac = "d4:d6:df:cd:4a:26")).shouldBeEmpty()
    }
}
