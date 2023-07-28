package com.bkahlert.netmon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Network(
    @SerialName("hostname") val hostname: String,
    @SerialName("interface") val `interface`: String,
    @SerialName("cidr") val cidr: Cidr,
) {
    override fun toString(): String = "$hostname:${`interface`}($cidr)"
}
