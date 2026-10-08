package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.scanner.discovery.mdns.MdnsLookup
import com.bkahlert.netmon.scanner.discovery.ssdp.SsdpLookup

/**
 * Model, vendor and kind from the UPnP description a host announced over SSDP.
 *
 * On a host whose `_airplay` record carries `rmodel`, a receiver app answers SSDP, so its manufacturer is not the host's.
 */
class SsdpClues(
    private val ssdp: SsdpLookup,
    private val mdns: MdnsLookup,
) : ClueSource {

    override fun clues(host: Host): List<Clue> {
        val device = ssdp.device(host.ip) ?: return emptyList()
        val model = device.modelNumber?.trim()?.takeUnless { it.isEmpty() || BARE_VERSION.matches(it) } ?: device.modelName?.trim()?.takeIf { it.isNotEmpty() }
        val vendor = device.manufacturer?.trim()?.takeIf { it.isNotEmpty() && !emulator(host) }
        return listOfNotNull(
            model?.let { Clue.Model(it, Source.PROTOCOL) },
            vendor?.let { Clue.Vendor(VendorNames.normalize(it), Source.PROTOCOL) },
            device.deviceType?.let(::kindOf)?.let { Clue.DeviceKind(it, Source.PROTOCOL) },
        )
    }

    private fun emulator(host: Host): Boolean =
        mdns.services(host.ip).orEmpty().any { it.application == "airplay" && !it.properties["rmodel"]?.text.isNullOrBlank() }

    private fun kindOf(deviceType: String): Kind? = when {
        deviceType.contains(":NAS:") -> Kind.STORAGE
        deviceType.contains(":InternetGatewayDevice:") || deviceType.contains(":fritzbox:") -> Kind.ROUTER
        deviceType.contains("lge:device:tv") -> Kind.TELEVISION
        deviceType.contains(":ZonePlayer:") -> Kind.SPEAKER
        deviceType.contains(":Printer:") -> Kind.PRINTER
        else -> null
    }

    companion object {
        private val BARE_VERSION = Regex("""\d+(?:\.\d+)*""")
    }
}
