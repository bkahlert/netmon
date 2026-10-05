package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.model_identification.DeviceModelCodesLookup

/**
 * Tells a genuine Apple model code from a spoofed one, and the kind of device a code names.
 *
 * Pis, NAS boxes and AirPlay receiver apps advertise Apple codes to get a Finder icon; a code is believed only for a
 * host whose MAC is Apple's, private or unknown, and that does not announce itself as a Linux host.
 */
class AppleCodes(private val codes: DeviceModelCodesLookup) {

    /** Returns [code] without Apple's `@ECOLOR=…` suffix. */
    fun normalize(code: String): String = code.substringBefore('@')

    /** Returns `true` for a code of Apple's shape (`iPad8,3`, `AirPort4`) that the table lists; the table's custom codes such as `One SL` are not Apple codes. */
    fun isKnown(code: String): Boolean = normalize(code).let { it.matches(APPLE_SHAPE) && it in codes }

    /** Returns `true` if [code] is a known Apple code and the host is one that could genuinely run it. */
    fun accepts(code: String, ouiVendor: String?, mac: String?, linuxHost: Boolean): Boolean =
        isKnown(code) && !linuxHost &&
            (ouiVendor == null || ouiVendor.startsWith("Apple", ignoreCase = true) || (mac != null && MacAddresses.isPrivate(mac)))

    /** Returns the kind the code's symbol family stands for, or `null` for an unknown code or family. */
    fun kindOf(code: String): Kind? {
        val symbol = codes.symbolName(normalize(code)) ?: return null
        return when {
            symbol.startsWith("ipad") -> Kind.TABLET
            symbol.startsWith("iphone") -> Kind.SMARTPHONE
            symbol.startsWith("macbook") -> Kind.LAPTOP
            symbol.startsWith("mac") || symbol.startsWith("imac") || symbol.startsWith("desktop") || symbol.startsWith("xserve") || symbol == "pc" -> Kind.COMPUTER
            symbol.startsWith("appletv") -> Kind.SET_TOP_BOX
            symbol.startsWith("homepod") || symbol.startsWith("hifispeaker") -> Kind.SPEAKER
            symbol.startsWith("applewatch") -> Kind.SMART_WATCH
            symbol.startsWith("airport") -> Kind.ROUTER
            symbol == "display" -> Kind.MONITOR
            else -> null
        }
    }

    companion object {
        private val APPLE_SHAPE = Regex("^[A-Za-z]+\\d+(?:,\\d+)?$")
    }
}
