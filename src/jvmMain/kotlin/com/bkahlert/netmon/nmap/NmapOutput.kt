package com.bkahlert.netmon.nmap

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class NmapOutput(
    @SerialName("nmaprun") val nmapRun: NmapRun,
) {
    @Serializable
    data class NmapRun(
        val target: List<Target>?,
        val host: List<Host>?,
    )

    @Serializable
    data class Target(
        val specification: String,
        val status: String? = null,
        val reason: String? = null
    )

    @Serializable
    data class Host(
        val status: Status,
        @Serializable(SingleElementUnwrappingJsonArraySerializer::class) val address: List<Address>,
        val hostnames: Hostnames? = null,
    ) {
        @Serializable
        data class Status(
            @SerialName("@state") val state: HostStates,
            @SerialName("@reason") val reason: String,
            @SerialName("@reason_ttl") val reasonTtl: String
        ) {
            @Serializable
            enum class HostStates { up, down, unknown, skipped }
        }

        @Serializable
        data class Address(
            @SerialName("@addr") val addr: String,
            @SerialName("@addrtype") val addrType: AttrType,
            @SerialName("@vendor") val vendor: String? = null
        ) {
            @Serializable
            enum class AttrType { ipv4, ipv6, mac }
        }

        @Serializable
        data class Hostnames(val hostname: Hostname?)

        @Serializable
        data class Hostname(
            @SerialName("@name") val name: String? = null,
            @SerialName("@type") val type: String? = null,
        )
    }
}

/**
 * [Json] serializer that represents lists with a single element
 * as the [JsonElement] itself (unwrapped), and otherwise as a [JsonArray].
 */
internal class SingleElementUnwrappingJsonArraySerializer<T>(serializer: KSerializer<T>) : JsonTransformingSerializer<List<T>>(ListSerializer(serializer)) {
    override fun transformSerialize(element: JsonElement): JsonElement {
        return if (element is JsonArray && element.size == 1) element.first() else element
    }

    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element !is JsonArray) JsonArray(listOf(element)) else element
}
