package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind

/** Brands and product words in device names, each telling the vendor, the kind, or both. */
object NameTokens {

    data class Token(val pattern: Regex, val vendor: String? = null, val kind: Kind? = null)

    private fun token(pattern: String, vendor: String? = null, kind: Kind? = null) =
        Token(Regex(pattern, RegexOption.IGNORE_CASE), vendor, kind)

    val tokens: List<Token> = listOf(
        token("^LEDVANCE", "Ledvance", Kind.LAMP),
        token("^Ring", "Ring", Kind.DOOR_BELL),
        token("^Sonoff", "Sonoff", Kind.SOCKET),
        token("^tado", "tado", Kind.HUB),
        token("^FYTA", "FYTA", Kind.HUB),
        token("^net[-_]ac[-_]", "Midea", Kind.AIR_CONDITIONER),
        token("nanoleaf", "Nanoleaf", Kind.LAMP),
        token("^Sonos", "Sonos", Kind.SPEAKER),
        token("hue", "Signify", Kind.HUB),
        token("zhimi|airpurifier", "Xiaomi", Kind.AIR_PURIFIER),
        token("^espressif$|^ESP[-_]", kind = Kind.CIRCUIT_BOARD),
        token("iphone", kind = Kind.SMARTPHONE),
        token("ipad", kind = Kind.TABLET),
        token("macbook", kind = Kind.LAPTOP),
        token("pi-?hole|homeassist", kind = Kind.COMPUTER),
        token("webostv|^tv\\d", kind = Kind.TELEVISION),
        token("cam\\b|camera", kind = Kind.CAMERA),
        token("^NPI|laserjet|printer", kind = Kind.PRINTER),
        token("homepod", kind = Kind.SPEAKER),
    )

    /** Returns the tokens found in [name], in table order. */
    fun matches(name: String): List<Token> = tokens.filter { it.pattern.containsMatchIn(name) }
}
