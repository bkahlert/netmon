package com.bkahlert.netmon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * MQTT topic: `dt/netmon/home`
 * @see <a href="https://docs.aws.amazon.com/whitepapers/latest/designing-mqtt-topics-aws-iot-core/mqtt-design-best-practices.html">MQTT Design Best Practices</a>
 */
@Serializable
@JsonClassDiscriminator("event")
sealed interface ScanEvent {

    val source: String

    @Serializable
    @SerialName("scan-restored")
    data class ScanRestoredEvent(
        override val source: String,
        val scan: ScanResult,
    ) : ScanEvent {
        override fun toString(): String = "${this::class.simpleName}(count=${scan.hosts.size})"
    }

    @Serializable
    @SerialName("scan-completed")
    data class ScanCompletedEvent(
        override val source: String,
        val scan: ScanResult,
    ) : ScanEvent {
        override fun toString(): String = "${this::class.simpleName}(count=${scan.hosts.size})"
    }

    @Serializable
    @SerialName("host-down")
    data class HostDownEvent(
        override val source: String,
        val host: Host,
    ) : ScanEvent

    @Serializable
    @SerialName("host-up")
    data class HostUpEvent(
        override val source: String,
        val host: Host,
    ) : ScanEvent

    companion object
}
