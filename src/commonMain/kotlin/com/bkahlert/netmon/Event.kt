package com.bkahlert.netmon

import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import com.bkahlert.kommons.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.time.Duration

/**
 * MQTT topic: `dt/netmon/${unqualifiedHostname}/${event}`
 * @see <a href="https://docs.aws.amazon.com/whitepapers/latest/designing-mqtt-topics-aws-iot-core/mqtt-design-best-practices.html">MQTT Design Best Practices</a>
 */
@Serializable
@JsonClassDiscriminator("event")
sealed interface Event {

    @Serializable
    @SerialName("scan")
    data class ScanEvent(
        @SerialName("type") val type: Type,
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

/** The passed time since this scan was done. */
val Event.ScanEvent.timePassed: Duration
    get() = (Now - timestamp).coerceAtLeast(Duration.ZERO)

/** Whether this scan is no more current. */
val Event.ScanEvent.outdated: Boolean
    get() = timePassed > Settings.SCAN_OUTDATED_THRESHOLD
