package com.bkahlert.netmon.identity

/** Short vendor names for the long or legal ones of the OUI table and protocol records. */
object VendorNames {

    private val aliases: List<Pair<String, String>> = listOf(
        "Raspberry Pi" to "Raspberry Pi",
        "Beijing Xiaomi" to "Xiaomi",
        "GD Midea" to "Midea",
        "AVM Audiovisuelles" to "AVM",
        "FRITZ!" to "AVM",
        "Philips Lighting" to "Signify",
        "Smart Innovation" to "eufy",
        "Amazon" to "Amazon",
        "Tuya" to "Tuya",
        "Apple" to "Apple",
        "Ugreen" to "Ugreen",
        "LG Electronics" to "LG",
        "Sonos" to "Sonos",
        "Hewlett Packard" to "HP",
        "QEMU" to "QEMU",
    )

    private val suffix = Regex(
        """[\s,]*(?:\([^)]*\)|\b(?:Inc\.?|Ltd\.?|Limited|GmbH|B\.?V\.?|Co\.?|Corp\.?|Corporation|Technologies|Group|Trading|Foundation)\.?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Returns the alias of [raw] if its start matches one, else [raw] without trailing legal suffixes. */
    fun normalize(raw: String): String {
        aliases.firstOrNull { (prefix, _) -> raw.startsWith(prefix, ignoreCase = true) }?.let { return it.second }
        var name = raw.trim()
        while (true) {
            val stripped = name.replace(suffix, "").trim().trimEnd(',').trim()
            if (stripped.isEmpty() || stripped == name) return name
            name = stripped
        }
    }
}
