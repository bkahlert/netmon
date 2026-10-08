package com.bkahlert.netmon.display.presentation

import com.bkahlert.netmon.contract.Kind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The icons the display draws for a host's kind, or for a specific vendor, model or name.
 *
 * Read-optimized like [DeviceModelCodes]: [kinds] maps a kind token to a symbol id, [specific] lists matchers to a
 * symbol id, [symbols] maps a symbol id to its SVG. `make device-icons` regenerates the resource [RESOURCE_NAME].
 */
@Serializable
data class DeviceIcons(
    @SerialName("kinds") private val kinds: Map<String, String> = emptyMap(),
    @SerialName("specific") private val specific: List<Matcher> = emptyList(),
    @SerialName("symbols") private val symbols: Map<String, String> = emptyMap(),
) : DeviceIconsLookup {

    override fun kindSymbol(kind: Kind): String? = kinds[kind.token]?.let(symbols::get)

    override fun specificSymbol(vendor: String?, model: String?, name: String?): String? =
        specific.firstOrNull { it.matches(vendor, model, name) }?.symbol?.let(symbols::get)

    override fun symbol(name: String): String? = symbols[name]

    /** Regexes over vendor, model and name; a given field must match, an absent host value never matches a given regex. */
    @Serializable
    data class Matcher(
        @SerialName("vendor") val vendor: String? = null,
        @SerialName("model") val model: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("symbol") val symbol: String,
    ) {
        fun matches(vendor: String?, model: String?, name: String?): Boolean =
            matches(this.vendor, vendor) && matches(this.model, model) && matches(this.name, name)

        private fun matches(pattern: String?, value: String?): Boolean =
            pattern == null || (value != null && Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(value))
    }

    companion object : DeviceIconsLookup {
        private var instance: DeviceIcons = DeviceIcons()
        override fun kindSymbol(kind: Kind): String? = instance.kindSymbol(kind)
        override fun specificSymbol(vendor: String?, model: String?, name: String?): String? = instance.specificSymbol(vendor, model, name)
        override fun symbol(name: String): String? = instance.symbol(name)

        /** Sets the [DeviceIcons] singleton to the specified [deviceIcons]. */
        fun set(deviceIcons: DeviceIcons): DeviceIcons = deviceIcons.also { instance = it }

        /** The name of the resource containing the [DeviceIcons]. */
        const val RESOURCE_NAME: String = "assets/device-icons.json"
    }
}

interface DeviceIconsLookup {
    /** Returns the SVG drawn for [kind], or `null` if the kind has none. */
    fun kindSymbol(kind: Kind): String?

    /** Returns the SVG of the first specific matcher that fits [vendor], [model] and [name], or `null`. */
    fun specificSymbol(vendor: String?, model: String?, name: String?): String?

    /** Returns the SVG of the symbol with the given id, for example `mdi:wifi`, or `null`. */
    fun symbol(name: String): String?
}
