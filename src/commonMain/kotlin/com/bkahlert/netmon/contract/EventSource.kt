package com.bkahlert.netmon.contract

import com.bkahlert.netmon.support.text.Template

/** Source of an event, specified by who ([node], [interface]) scanned what ([cidr]).  */
data class EventSource(
    val node: String,
    val `interface`: String,
    val cidr: Cidr,
) {
    private val text by lazy { "$node/${`interface`}/$cidr" }

    override fun toString(): String = text

    companion object {
        fun fromTopic(topic: String, template: Template): EventSource {
            val match = ScanTopics.pattern(template).matchEntire(topic) ?: throw IllegalArgumentException("Invalid topic: $topic")
            val node = match.groups["node"]?.value ?: throw IllegalArgumentException("Template has no node field: $template")
            val `interface` = match.groups["interface"]?.value ?: throw IllegalArgumentException("Template has no interface field: $template")
            val cidr = match.groups["cidr"]?.value ?: throw IllegalArgumentException("Template has no cidr field: $template")
            return EventSource(node, `interface`, Cidr.parse(cidr))
        }
    }
}
