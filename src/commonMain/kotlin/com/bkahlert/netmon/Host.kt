package com.bkahlert.netmon

import com.bkahlert.netmon.serialization.InstantAsEpochSecondsSerializer
import kotlin.time.Clock
import kotlin.time.Instant
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
    @SerialName("services") val services: Set<String>? = null,
    /** The time of the last scan that found the host up. */
    @SerialName("lastSeen") @Serializable(InstantAsEpochSecondsSerializer::class) val lastSeen: Instant? = null,
) {
    companion object
}

/** Computes the time passed since this host changed its status. */
@Suppress("NOTHING_TO_INLINE")
inline fun Host.getElapsedTime(now: Instant = Clock.System.now()): Duration? =
    since?.let { now - it }?.coerceAtLeast(Duration.ZERO)
