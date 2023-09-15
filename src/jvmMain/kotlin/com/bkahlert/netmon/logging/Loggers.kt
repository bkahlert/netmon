package com.bkahlert.netmon.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.bkahlert.kommons.logging.logback.Logback
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/** Returns the logger with the given [name]. */
operator fun Logback.get(name: String): Logger =
    if (Logger.ROOT_LOGGER_NAME.equals(name, ignoreCase = true)) rootLogger
    else LoggerFactory.getILoggerFactory().getLogger(name) as? Logger ?: error("Cannot get logger $name")

/** Returns the logger with the given [clazz]. */
operator fun Logback.get(clazz: KClass<*>): Logger =
    (LoggerFactory.getILoggerFactory() as? LoggerContext)?.getLogger(clazz.java) ?: error("Cannot get logger for $clazz")

/** Applies the given [levels] to their respective loggers. */
fun Logback.levels(levels: Iterable<Pair<String, ch.qos.logback.classic.Level>>): Unit =
    levels.forEach { (name, level) -> this[name].level = level }

/** Applies the given [levels] to their respective loggers. */
fun Logback.levels(vararg levels: Pair<String, ch.qos.logback.classic.Level>): Unit =
    levels(levels.asList())

/** Applies the given [levels] to their respective loggers. */
fun Logback.levels(levels: Map<String, ch.qos.logback.classic.Level>): Unit =
    levels(levels.entries.map { it.toPair() })
