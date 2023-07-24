package com.bkahlert.netmon

import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Host(
    @SerialName("ip") val ip: String?,
    @SerialName("name") val name: String? = null,
    @SerialName("status") val status: Status? = null,
    @SerialName("first-up") @Serializable(InstantAsEpochSecondsSerializer::class) val firstUp: Instant? = null,
) {

    companion object
}
