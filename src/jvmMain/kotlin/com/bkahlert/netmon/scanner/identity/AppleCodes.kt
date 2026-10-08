package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Kind

/**
 * Tells a genuine Apple model code from a spoofed one, and the kind of device a code names.
 *
 * Pis, NAS boxes and AirPlay receiver apps advertise Apple codes to get a Finder icon; a code is believed only for a
 * host whose MAC is Apple's, private or unknown, and that does not announce itself as a Linux host.
 */
class AppleCodes(private val codes: ModelCatalogLookup) {

    /** Returns [code] without Apple's `@ECOLOR=…` suffix. */
    fun normalize(code: String): String = code.substringBefore('@')

    /** Returns `true` for a code of Apple's shape (`iPad8,3`, `AirPort4`) that the catalog lists; custom codes such as `One SL` are not Apple codes. */
    fun isKnown(code: String): Boolean = normalize(code).let { it.matches(APPLE_SHAPE) && it in codes }

    /** Returns `true` if [code] is a known Apple code and the host is one that could genuinely run it. */
    fun accepts(code: String, ouiVendor: String?, mac: String?, linuxHost: Boolean): Boolean =
        isKnown(code) && !linuxHost &&
            (ouiVendor == null || ouiVendor.startsWith("Apple", ignoreCase = true) || (mac != null && MacAddresses.isPrivate(mac)))

    /** Returns the catalog kind for [code], or `null` if it is unclassified. */
    fun kindOf(code: String): Kind? = codes.kindOf(normalize(code))

    companion object {
        private val APPLE_SHAPE = Regex("^[A-Za-z]+\\d+(?:,\\d+)?$")
    }
}
