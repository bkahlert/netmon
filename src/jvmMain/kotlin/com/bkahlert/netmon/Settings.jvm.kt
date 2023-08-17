package com.bkahlert.netmon

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
): Setting<T> = SystemPropertyAndEnvironmentSettings(name, default) { stringFormat.decodeFromString(deserializer, it) }

private class SystemPropertyAndEnvironmentSettings<T, D : T>(
    val name: String?,
    val default: D,
    val parse: (String) -> T,
) : Setting<T> {

    private val logger by SLF4J

    override fun getValue(thisRef: Settings?, property: KProperty<*>): T {
        val path = thisRef?.path.orEmpty().plus(name ?: property.name)
        val raw = System.getProperty(path.joinToString("."))
            ?: System.getenv(path.joinToString("_") { it.toScreamingSnakeCasedString() })
        logger.debug("Read raw value of {}: {}", kv("property", path), v("value", raw))
        return raw?.let { parse(it) } ?: default
    }
}
