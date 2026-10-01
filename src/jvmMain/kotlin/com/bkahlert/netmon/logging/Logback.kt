package com.bkahlert.netmon.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/** The Logback loggers behind SLF4J. */
object Logback {

    /** Returns the logger with the given [name]; `root` names the root logger. */
    operator fun get(name: String): Logger =
        LoggerFactory.getLogger(name) as? Logger ?: error("Cannot get logger $name")

    /** Returns the logger named after the given [clazz]. */
    operator fun get(clazz: KClass<*>): Logger = this[clazz.java.name]

    /** Applies the given [levels] to their respective loggers. */
    fun levels(levels: Map<String, Level>): Unit =
        levels.forEach { (name, level) -> this[name].level = level }
}
