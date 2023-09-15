package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.serialization.DataUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceModelCodes(
    /** A map containing all device model codes mapped to their most specific identifier shared by all conforming types. */
    @SerialName("code-identifier-mappings") private val deviceModelCodeToIdentifier: Map<String, String>? = null,
    /** A map containing all identifiers mapped to their description. */
    @SerialName("identifier-description-mappings") private val identifierDescriptionMappings: Map<String, String?>? = null,
    /** A map containing all identifiers mapped to their icon. */
    @SerialName("identifier-icon-mappings") private val identifierIconMappings: Map<String?, String?>? = null,
    /** A map containing embedded icons. */
    @SerialName("embedded-icons") private val embeddedIcons: Map<String, DataUrl?>? = null,
) : DeviceModelCodesLookup, Set<String> by deviceModelCodeToIdentifier?.keys.orEmpty() {

    override fun identifier(deviceModelCode: String): String? = deviceModelCodeToIdentifier
        ?.get(deviceModelCode)

    override fun description(deviceModelCode: String): String? = identifier(deviceModelCode)
        ?.let { identifierDescriptionMappings?.get(it) }

    override fun icon(deviceModelCode: String): DeviceModelCodesLookup.Image? = identifier(deviceModelCode)
        .let { identifierIconMappings?.get(it) }
        ?.let { DeviceModelCodesLookup.Image(it, embeddedIcons?.get(it)?.url ?: it) }

    override fun toString(): String = (takeIf { size <= 5 } ?: (take(3) + "...").plus(last())).toString()


    companion object : DeviceModelCodesLookup, AbstractSet<String>() {
        private val EMPTY = DeviceModelCodes()
        private var instance: DeviceModelCodes = EMPTY
        override val size: Int get() = instance.size
        override fun iterator(): Iterator<String> = instance.iterator()
        override fun identifier(deviceModelCode: String): String? = instance.identifier(deviceModelCode)
        override fun description(deviceModelCode: String): String? = instance.description(deviceModelCode)
        override fun icon(deviceModelCode: String): DeviceModelCodesLookup.Image? = instance.icon(deviceModelCode)

        /** Sets the [DeviceModelCodes] singleton to be the specified [deviceModelCodes]. */
        fun set(deviceModelCodes: DeviceModelCodes): DeviceModelCodes = deviceModelCodes.also { instance = deviceModelCodes }

        /** The name of the resource containing the [DeviceModelCodes] mappings. */
        const val RESOURCE_NAME: String = "assets/device-model-codes.json"
    }
}

/** Set of device model codes that can be resolved information of their most specific conforming type specification. */
interface DeviceModelCodesLookup : Set<String> {
    /** Returns the type identifier of the specified [deviceModelCode]. */
    fun identifier(deviceModelCode: String): String?

    /** Returns the description of the most specific conforming type of the specified [deviceModelCode]. */
    fun description(deviceModelCode: String): String?

    /** Returns the icon of the most specific conforming type of the specified [deviceModelCode]. */
    fun icon(deviceModelCode: String): Image?

    /** An image with a [name] and a [source]. */
    data class Image(
        /** The name of the image. */
        val name: String,
        /** The source of the image. */
        val source: String,
    ) {
        override fun toString(): String = "Image($name: ${source.takeIf { it.length < 40 } ?: source.take(20).plus(" … ").plus(source.takeLast(10))})"
    }
}
