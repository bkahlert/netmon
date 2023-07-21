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
sealed class ScanEvent {

    fun publish() {
        println(this)
    }

    @Serializable
    @SerialName("scan-restored")
    data class ScanRestoredEvent(val result: ScanResult) : ScanEvent() {
        override fun toString(): String = "${this::class.simpleName}(up=${result.hosts.size})"
    }

    @Serializable
    @SerialName("scan-completed")
    data class ScanCompletedEvent(val result: ScanResult) : ScanEvent() {
        override fun toString(): String = "${this::class.simpleName}(up=${result.hosts.size})"
    }

    @Serializable
    @SerialName("host-down")
    data class HostDownEvent(val host: Host) : ScanEvent()

    @Serializable
    @SerialName("host-up")
    data class HostUpEvent(val host: Host) : ScanEvent()
}
