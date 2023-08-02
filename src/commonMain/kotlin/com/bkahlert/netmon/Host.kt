package com.bkahlert.netmon

import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import com.bkahlert.kommons.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class Host(
    @SerialName("ip") val ip: IP,
    @SerialName("name") val name: String? = null,
    @SerialName("status") val status: Status? = null,
    @SerialName("since") @Serializable(InstantAsEpochSecondsSerializer::class) val since: Instant? = null,
    /** A string that identifies the device model. */
    @SerialName("model") val model: String? = null,
    @SerialName("vendor") val vendor: String? = null,
    @SerialName("services") val services: List<String> = emptyList(),
) {
    companion object
}

val Host.timePassed: Duration?
    get() = since?.let { Now - it }?.coerceAtLeast(Duration.ZERO)

val Host.stable: Boolean
    get() = timePassed?.let { it > Settings.HOST_STATE_CHANGE_STABLE_DURATION } ?: true
