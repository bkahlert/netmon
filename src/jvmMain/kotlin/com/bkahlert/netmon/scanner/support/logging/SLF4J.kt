package com.bkahlert.netmon.scanner.support.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KProperty

/**
 * Provides a lazily created SLF4J [Logger] named after the declaring class, as in `private val logger by SLF4J`.
 *
 * A companion object's logger carries the enclosing class's name, a top-level property's the file class's.
 */
object SLF4J {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Lazy<Logger> {
        val name = loggerName(thisRef)
        return lazy { LoggerFactory.getLogger(name) }
    }

    private fun loggerName(thisRef: Any?): String = when (thisRef) {
        null -> Throwable().stackTrace.first { it.className != SLF4J::class.java.name }.className
        else -> thisRef.javaClass.let { clazz ->
            clazz.enclosingClass?.takeIf { clazz.simpleName == "Companion" }?.name ?: clazz.name
        }
    }
}
