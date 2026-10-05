package com.bkahlert.netmon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a host is attached to the LAN, as the router reports it. */
@Serializable
enum class Link {
    @SerialName("ethernet") ETHERNET,
    @SerialName("wifi") WIFI,
}
