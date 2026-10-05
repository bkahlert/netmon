package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.mdns.MdnsLookup
import com.bkahlert.netmon.mdns.ServiceInfo

/**
 * Clues from the mDNS records announced at a host's IP: instance names, vendor model fields, HomeKit categories and
 * Apple model codes, in the order the spec ranks them within a source.
 */
class MdnsClues(
    private val mdns: MdnsLookup,
    private val appleCodes: AppleCodes,
) : ClueSource {

    override fun clues(host: Host): List<Clue> {
        val services = mdns.services(host.ip).orEmpty()
        if (services.isEmpty()) return emptyList()
        val servers = mdns.servers(host.ip).orEmpty()
        val records = Records(services)
        val emulator = records.txt("airplay", "rmodel") != null
        val machine = records.txt("device-info", "machine")
        val linuxHost = records.has("workstation") || machine != null

        return buildList {
            names(records, servers)
            models(records, emulator, machine)
            appleCode(records, host, linuxHost)
            vendors(records, emulator)
            kinds(records, machine)
        }
    }

    private fun MutableList<Clue>.names(records: Records, servers: Set<String>) {
        listOfNotNull(
            records.instance("device-info"),
            records.instance("hap"),
            records.instance("airplay"),
            records.txt("googlecast", "fn"),
            records.txt("amzn-wplay", "n"),
            records.instance("sonos")?.substringAfter('@'),
            records.instance("nanoleafapi"),
            records.instance("ipp"),
            records.instance("companion-link"),
        ).forEach { add(Clue.Name(it, Source.PROTOCOL)) }
        servers.forEach { add(Clue.Name(it, Source.MDNS_HOST)) }
    }

    private fun MutableList<Clue>.models(records: Records, emulator: Boolean, machine: String?) {
        listOfNotNull(
            machine,
            records.txt("hap", "md"),
            records.txt("nanoleafapi", "md"),
            records.txt("amzn-wplay", "n"),
            records.txt("matterd", "DN"),
            records.txt("ipp", "ty") ?: records.txt("ipp", "usb_MDL"),
            records.txt("hue", "modelid"),
            records.txt("meshcop", "mn")?.takeUnless { it == BORDER_ROUTER },
        ).forEach { add(Clue.Model(it, Source.PROTOCOL)) }
        if (!emulator) {
            records.txt("googlecast", "md")?.let { add(Clue.Model(it, Source.PROTOCOL)) }
            listOfNotNull(records.txt("airplay", "model"), records.txt("raop", "am"))
                .filterNot(appleCodes::isKnown)
                .forEach { add(Clue.Model(it, Source.PROTOCOL)) }
        }
    }

    private fun MutableList<Clue>.appleCode(records: Records, host: Host, linuxHost: Boolean) {
        val code = listOfNotNull(
            records.txt("device-info", "model"),
            records.txt("airplay", "model"),
            records.txt("raop", "am"),
            records.txt("companion-link", "rpMd"),
        ).firstOrNull { appleCodes.accepts(it, ouiVendor = host.vendor, mac = host.mac, linuxHost = linuxHost) } ?: return
        val normalized = appleCodes.normalize(code)
        add(Clue.Model(normalized, Source.APPLE_CODE))
        add(Clue.Vendor("Apple", Source.APPLE_CODE))
        appleCodes.kindOf(normalized)?.let { add(Clue.DeviceKind(it, Source.APPLE_CODE)) }
    }

    private fun MutableList<Clue>.vendors(records: Records, emulator: Boolean) {
        listOfNotNull(
            records.txt("airplay", "manufacturer")?.takeUnless { emulator },
            records.txt("ipp", "usb_MFG"),
            records.txt("meshcop", "vn")?.takeUnless { it == OPEN_THREAD },
        ).forEach { add(Clue.Vendor(VendorNames.normalize(it), Source.PROTOCOL)) }
    }

    private fun MutableList<Clue>.kinds(records: Records, machine: String?) {
        fun kind(kind: Kind) = add(Clue.DeviceKind(kind, Source.PROTOCOL))
        HapCategories.kindOf(records.txt("hap", "ci"))?.let(::kind)
        if (records.has("amzn-wplay")) kind(Kind.SET_TOP_BOX)
        if (records.txt("matterd", "DT") == MATTER_CASTING_VIDEO_PLAYER) kind(Kind.SET_TOP_BOX)
        if (records.has("ipp") || records.has("printer")) kind(Kind.PRINTER)
        if (records.has("hue")) kind(Kind.HUB)
        if (records.txt("meshcop", "mn") == BORDER_ROUTER) kind(Kind.HUB)
        if (records.has("nanoleafapi")) kind(Kind.LAMP)
        if (records.txt("ewelink", "type") == "plug") kind(Kind.SOCKET)
        if (records.has("googlecast")) kind(Kind.TELEVISION)
        if (records.has("sonos") || records.has("raop") || records.has("airplay")) kind(Kind.SPEAKER)
        if (machine?.contains("Raspberry Pi") == true) kind(Kind.CIRCUIT_BOARD)
    }

    private class Records(private val services: Set<ServiceInfo>) {
        fun has(application: String): Boolean = services.any { it.application == application }
        fun instance(application: String): String? = services.firstOrNull { it.application == application }?.name?.takeIf { it.isNotBlank() }
        fun txt(application: String, key: String): String? =
            services.firstOrNull { it.application == application }?.properties?.get(key)?.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val BORDER_ROUTER = "BorderRouter"
        private const val OPEN_THREAD = "OpenThread"
        private const val MATTER_CASTING_VIDEO_PLAYER = "35"
    }
}
