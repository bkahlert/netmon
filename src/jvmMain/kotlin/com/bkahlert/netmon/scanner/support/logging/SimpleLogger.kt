package com.bkahlert.netmon.scanner.support.logging

/**
 * slf4j-simple's levels through its system properties.
 *
 * A level applies to loggers created after it is set; the root level is read once, at the first logger.
 */
object SimpleLogger {

    /** The name standing for the default level of every logger without one of its own. */
    const val ROOT = "root"

    /** Sets each logger's level; [ROOT] sets the default level. */
    fun configure(levels: Map<String, LogLevel>): Unit =
        levels.forEach { (name, level) -> System.setProperty(key(name), level.name.lowercase()) }

    /** Returns the level set for [name], or `null` if none is set. */
    fun level(name: String): LogLevel? = System.getProperty(key(name))?.let { LogLevel.valueOf(it.uppercase()) }

    private fun key(name: String): String =
        if (name == ROOT) "org.slf4j.simpleLogger.defaultLogLevel" else "org.slf4j.simpleLogger.log.$name"
}
