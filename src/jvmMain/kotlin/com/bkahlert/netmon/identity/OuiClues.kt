package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind

/** The vendor nmap read from the MAC prefix table, normalized, and the kind that vendor's devices usually are. */
class OuiClues : ClueSource {

    override fun clues(host: Host): List<Clue> {
        val mac = host.mac
        if (mac != null && MacAddresses.isPrivate(mac)) return emptyList()
        val vendor = host.vendor?.let(VendorNames::normalize) ?: return emptyList()
        return listOfNotNull(
            Clue.Vendor(vendor, Source.OUI),
            KIND_DEFAULTS[vendor]?.let { Clue.DeviceKind(it, Source.OUI) },
        )
    }

    companion object {
        private val KIND_DEFAULTS: Map<String, Kind> = mapOf(
            "Nintendo" to Kind.GAMING_DEVICE,
            "Ring" to Kind.DOOR_BELL,
            "Tuya" to Kind.SOCKET,
            "Espressif" to Kind.CIRCUIT_BOARD,
            "Raspberry Pi" to Kind.CIRCUIT_BOARD,
            "Amazon" to Kind.SPEAKER,
            "Sonos" to Kind.SPEAKER,
            "Nanoleaf" to Kind.LAMP,
            "Midea" to Kind.AIR_CONDITIONER,
            "Signify" to Kind.HUB,
            "HP" to Kind.PRINTER,
            "LG" to Kind.TELEVISION,
            "AVM" to Kind.ROUTER,
        )
    }
}
