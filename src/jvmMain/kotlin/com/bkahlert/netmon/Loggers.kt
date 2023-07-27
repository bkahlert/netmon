package com.bkahlert.netmon

import ch.qos.logback.classic.Logger
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.Logback

operator fun Logback.get(name: String): Logger = when (name) {
    "root" -> Logback.rootLogger
    else -> SLF4J.getLogger(name) as? Logger ?: error("Cannot get logger $name")
}

fun Logback.levels(vararg levels: Pair<String, ch.qos.logback.classic.Level>) {
    levels.forEach { (name, level) ->
        this[name].level = level
    }
}
