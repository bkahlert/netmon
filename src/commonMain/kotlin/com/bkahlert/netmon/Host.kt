package com.bkahlert.netmon

import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Host(
    @SerialName("ip") val ip: IP,
    @SerialName("name") val name: String? = null,
    @SerialName("status") val status: Status? = null,
    @SerialName("since") @Serializable(InstantAsEpochSecondsSerializer::class) val since: Instant? = null,
    /** A string that identifies the device model. */
    @SerialName("model") val model: String? = null,
    @SerialName("services") val services: List<String> = emptyList(),
) {
    companion object
}
