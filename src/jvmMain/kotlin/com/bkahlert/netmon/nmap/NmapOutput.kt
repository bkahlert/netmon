package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
        @Serializable(SingleElementUnwrappingJsonArraySerializer::class) val host: List<Host>?,
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
        val hostscript: Hostscript? = null,
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

        @Serializable
        data class Hostscript(val script: Script?)

        @Serializable
        data class Script(
            @SerialName("@id") val id: String? = null,
            @SerialName("@output") val output: String? = null,
        )
    }
}

/** The hosts in this [NmapOutput.NmapRun] as [Host] instances. */
val NmapOutput.NmapRun.hosts: List<Host>
    get() = host.orEmpty().mapNotNull { it.asHost() }

/** Returns this [NmapOutput.Host] as a [Host]. */
fun NmapOutput.Host.asHost(): Host? = ipAddress?.let { ip ->
    Host(
        ip = ip,
        name = name,
        status = status(),
        since = null,
        model = null,
        vendor = macAddress?.vendor,
        services = null,
    )
}

/** [IP] of this [NmapOutput.Host] or `null` if none found. */
val NmapOutput.Host.ipAddress: IP?
    get() = address.firstOrNull { it.addrType == NmapOutput.Host.Address.AttrType.ipv4 || it.addrType == NmapOutput.Host.Address.AttrType.ipv6 }
        ?.let { IP(it.addr) }

/** [NmapOutput.Host.Address.AttrType.mac]-typed [NmapOutput.Host.Address] of this [NmapOutput.Host] or `null` if none found. */
val NmapOutput.Host.macAddress: NmapOutput.Host.Address?
    get() = address.firstOrNull { it.addrType == NmapOutput.Host.Address.AttrType.mac }

/** Name of this [NmapOutput.Host] or `null` if none found. */
val NmapOutput.Host.name: String?
    get() = hostnames?.hostname?.name

/** [Status] of this [NmapOutput.Host] or `null` if none found. */
fun NmapOutput.Host.status(): Status =
    Status.of(status.state.name)

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
