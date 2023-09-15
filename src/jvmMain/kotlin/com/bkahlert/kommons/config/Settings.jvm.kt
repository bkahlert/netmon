package com.bkahlert.kommons.config

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.kommons.text.toScreamingSnakeCasedString
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat
import net.logstash.logback.argument.StructuredArguments.kv
import kotlin.reflect.KProperty

actual fun <T, D : T> setting(
    default: D,
    deserializer: DeserializationStrategy<T>,
    stringFormat: StringFormat,
    name: String?,
): Setting<T> = SystemPropertyAndEnvironmentSetting(name, default) { stringFormat.decodeFromString(deserializer, it) }

private class SystemPropertyAndEnvironmentSetting<T, D : T>(
    val name: String?,
    val default: D,
    val parse: (String) -> T,
) : Setting<T> {

    private val logger by SLF4J

    fun envKey(path: List<String>): String = path.joinToString("_") { it.toScreamingSnakeCasedString() }
    fun envValue(path: List<String>): String? = System.getenv(envKey(path))
    fun sysPropKey(path: List<String>): String = path.joinToString(".")
    fun sysPropValue(path: List<String>): String? = System.getProperty(sysPropKey(path))

    override fun getValue(thisRef: Settings?, property: KProperty<*>): T {
        val path = thisRef?.path.orEmpty().plus(name ?: property.name)
        val (value: T, source: String) = when (val systemPropertyValue = sysPropValue(path)) {
            null -> when (val environmentValue = envValue(path)) {
                null -> default to "default"
                else -> parse(environmentValue) to "env:${envKey(path)}"
            }

            else -> parse(systemPropertyValue) to "sysProp:${sysPropKey(path)}"
        }
        logger.debug(
            "{} resolved using {}: {}",
            kv("property", path.joinToString(".")),
            kv("source", source),
            v("value", value),
        )
        return value
    }
}
