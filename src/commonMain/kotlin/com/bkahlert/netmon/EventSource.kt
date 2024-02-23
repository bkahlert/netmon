package com.bkahlert.netmon

/** Source of an event, specified by who ([node], [interface]) scanned what ([cidr]).  */
data class EventSource(
    val node: String,
    val `interface`: String,
    val cidr: Cidr,
) {
    private val text by lazy { "$node/${`interface`}/$cidr" }

    override fun toString(): String = text

    companion object {

        val PATTERN = ScanEventSettings.topic.toString(
            "node" to "(?<node>[^/]+)",
            "interface" to "(?<interface>[^/]+)",
            "cidr" to "(?<cidr>${Cidr.PATTERN.pattern})",
        ).toRegex()

        fun fromTopic(topic: String): EventSource {
            val (node, `interface`, cidr) = PATTERN.matchEntire(topic)?.destructured ?: throw IllegalArgumentException("Invalid topic: $topic")
            return EventSource(node, `interface`, Cidr.parse(cidr))
        }
    }
}
