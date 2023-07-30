package com.bkahlert.netmon

import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * MQTT topic: `dt/netmon/${unqualifiedHostname}/${event}`
 * @see <a href="https://docs.aws.amazon.com/whitepapers/latest/designing-mqtt-topics-aws-iot-core/mqtt-design-best-practices.html">MQTT Design Best Practices</a>
 */
@Serializable
@JsonClassDiscriminator("event")
sealed interface Event {

    @SerialName("network")
    val network: Network

    @Serializable
    @SerialName("scan")
    data class ScanEvent(
        @SerialName("type") val type: Type,
        override val network: Network,
        @SerialName("hosts") val hosts: List<Host>,
        @SerialName("timestamp") @Serializable(InstantAsEpochSecondsSerializer::class) val timestamp: Instant,
    ) : Event {
        override fun toString(): String = "${this::class.simpleName}(type=$type,count=${hosts.size})"

        enum class Type {
            @SerialName("restored")
            RESTORED,

            @SerialName("completed")
            COMPLETED,
        }
    }

    @Serializable
    @SerialName("host")
    data class HostEvent(
        @SerialName("type") val type: Type,
        override val network: Network,
        val host: Host,
    ) : Event {
        enum class Type {
            @SerialName("up")
            UP,

            @SerialName("down")
            DOWN,
        }
    }

    companion object
}
