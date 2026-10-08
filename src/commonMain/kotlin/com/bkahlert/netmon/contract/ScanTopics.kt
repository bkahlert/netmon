package com.bkahlert.netmon.contract

import com.bkahlert.netmon.support.text.Template

object ScanTopics {

    fun topic(template: Template, source: EventSource): String = template.toString(
        "node" to source.node,
        "interface" to source.`interface`,
        "cidr" to source.cidr.toString(),
    )

    fun subscription(template: Template): String = template.toString(
        template.fields.associateWith { field -> if (field == "cidr") "+/+" else "+" },
    )

    fun pattern(template: Template): Regex {
        val placeholders = Regex("""\$\{(?<field>[^}]+)\}""")
        return buildString {
            var offset = 0
            placeholders.findAll(template.text).forEach { match ->
                append(Regex.escape(template.text.substring(offset, match.range.first)))
                val field = checkNotNull(match.groups["field"]).value
                append(
                    when (field) {
                        "node" -> "(?<node>[^/]+)"
                        "interface" -> "(?<interface>[^/]+)"
                        "cidr" -> "(?<cidr>${Cidr.PATTERN.pattern})"
                        else -> "[^/]+"
                    },
                )
                offset = match.range.last + 1
            }
            append(Regex.escape(template.text.substring(offset)))
        }.toRegex()
    }
}
