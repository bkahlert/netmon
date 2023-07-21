package com.bkahlert.netmon

import com.bkahlert.time.timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Host(
    @SerialName("ip") val ip: String?,
    @SerialName("name") val name: String? = null,
    @SerialName("status") val status: Status? = null,
    @SerialName("first-up") val firstUp: Long? = if (status == Status.UP) timestamp() else null,
) {

    companion object {
        fun parse(line: String): Host {
            var ip: String? = null
            var host: String? = null
            var status: Status? = null
            line.split("\t")
                .map { it.split(": ", limit = 2) }
                .forEach {
                    when (it.first().lowercase()) {
                        "host" -> {
                            ip = it.last().split(" ").first()
                            host = it.last().split(" ").last().removeSurrounding("(", ")")
                        }

                        "status" -> status = Status.of(it.last())
                    }
                }
            return Host(ip, host, status)
        }
    }
}
