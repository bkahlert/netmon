package com.bkahlert.netmon.contract

import com.bkahlert.netmon.contract.serialization.InstantAsEpochSecondsSerializer
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
    /** The hardware model as a device or protocol reports it, with Apple's `@ECOLOR=…` suffix stripped. */
    @SerialName("model") val model: String? = null,
    @SerialName("vendor") val vendor: String? = null,
    @SerialName("services") val services: Set<String>? = null,
    /** The time of the last scan that found the host up. */
    @SerialName("lastSeen") @Serializable(InstantAsEpochSecondsSerializer::class) val lastSeen: Instant? = null,
    /** The hardware address, lowercase with colons; it identifies the device across IP changes. */
    @SerialName("mac") val mac: String? = null,
    /** The class of device, `null` until the scanner resolved one. */
    @SerialName("kind") val kind: Kind? = null,
    /** How the host is attached to the LAN, as the router reports it. */
    @SerialName("link") val link: Link? = null,
    /** The speed of the router's port or Wi-Fi link to the host; for a wired host behind a switch, the port's, not the host's. */
    @SerialName("speed") val speed: LinkSpeed? = null,
) {
    companion object
}

/** Computes the time passed since this host changed its status. */
@Suppress("NOTHING_TO_INLINE")
inline fun Host.getElapsedTime(now: Instant = Clock.System.now()): Duration? =
    since?.let { now - it }?.coerceAtLeast(Duration.ZERO)
