package com.bkahlert.netmon

data class EventSource(
    val node: String,
    val `interface`: String,
    val cidr: Cidr,
) {
    override fun toString(): String = "$node/${`interface`}/$cidr"

    companion object {
        fun fromTopic(topic: String): EventSource {
            val (node, iface, ip, mask) = topic.split("/").dropLast(1).takeLast(4)
            return EventSource(node, iface, Cidr("$ip/$mask"))
        }
    }
}
