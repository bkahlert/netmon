package com.bkahlert.netmon.display.presentation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The device model codes the scanner knows and the display draws, each with the description and SF Symbol of its type.
 *
 * Read-optimized: [models] maps a model code to its description and symbol name, [symbols] a symbol name to its SVG,
 * so a lookup is two map reads. `make device-model-codes` regenerates the resource [RESOURCE_NAME] from device-icons.
 */
@Serializable
data class DeviceModelCodes(
    @SerialName("models") private val models: Map<String, Model> = emptyMap(),
    @SerialName("symbols") private val symbols: Map<String, String> = emptyMap(),
) : DeviceModelCodesLookup, Set<String> by models.keys {

    override fun description(deviceModelCode: String): String? = models[deviceModelCode]?.description

    override fun symbol(deviceModelCode: String): String? = models[deviceModelCode]?.symbol?.let { symbols[it] }

    override fun symbolName(deviceModelCode: String): String? = models[deviceModelCode]?.symbol

    override fun toString(): String = (takeIf { size <= 5 } ?: (take(3) + "...").plus(last())).toString()

    /** What is known of a model code: the description of its type and the name of the symbol it is drawn with, each if any. */
    @Serializable
    data class Model(
        @SerialName("description") val description: String? = null,
        @SerialName("symbol") val symbol: String? = null,
    )

    companion object : DeviceModelCodesLookup, AbstractSet<String>() {
        private val EMPTY = DeviceModelCodes()
        private var instance: DeviceModelCodes = EMPTY
        override val size: Int get() = instance.size
        override fun iterator(): Iterator<String> = instance.iterator()
        override fun description(deviceModelCode: String): String? = instance.description(deviceModelCode)
        override fun symbol(deviceModelCode: String): String? = instance.symbol(deviceModelCode)
        override fun symbolName(deviceModelCode: String): String? = instance.symbolName(deviceModelCode)

        /** Sets the [DeviceModelCodes] singleton to be the specified [deviceModelCodes]. */
        fun set(deviceModelCodes: DeviceModelCodes): DeviceModelCodes = deviceModelCodes.also { instance = deviceModelCodes }

        /** The name of the resource containing the [DeviceModelCodes]. */
        const val RESOURCE_NAME: String = "assets/device-model-codes.json"
    }
}

/** Set of device model codes that resolve to the description and symbol of their type. */
interface DeviceModelCodesLookup : Set<String> {
    /** Returns the description of the type of the specified [deviceModelCode], or `null` if the code is unknown or its type has none. */
    fun description(deviceModelCode: String): String?

    /** Returns the SVG of the symbol the specified [deviceModelCode] is drawn with, or `null` if the code is unknown or gets none. */
    fun symbol(deviceModelCode: String): String?

    /** Returns the name of the symbol the specified [deviceModelCode] is drawn with, or `null` if the code is unknown or gets none. */
    fun symbolName(deviceModelCode: String): String?
}
