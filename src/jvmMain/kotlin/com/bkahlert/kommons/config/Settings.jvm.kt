package com.bkahlert.kommons.config

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat

internal actual fun <T> settingValue(
    path: List<String>,
    deserializer: DeserializationStrategy<T>,
    stringFormat: StringFormat,
): Setting<T> = SystemOrEnvironmentSetting(path, deserializer, stringFormat)

class SystemOrEnvironmentSetting<T>(
    override val path: List<String>,
    private val deserializer: DeserializationStrategy<T>,
    private val stringFormat: StringFormat,
) : Setting<T> {
    override val origin: String get() = sysPropValue(path)?.let { "sys" } ?: envValue(path)?.let { "env" } ?: "sys|env"
    override val value: T? get() = (sysPropValue(path) ?: envValue(path))?.let { stringFormat.decodeFromString(deserializer, it) }
    override fun toString(): String = "$origin:${path.joinToString(".")}=$value"
}

class EnvironmentSetting<T>(
    override val path: List<String>,
    private val deserializer: DeserializationStrategy<T>,
    private val stringFormat: StringFormat,
) : Setting<T> {
    override val origin: String = "env"
    override val value: T? = envValue(path)?.let { stringFormat.decodeFromString(deserializer, it) }
    override fun toString(): String = "$origin:${path.joinToString(".")}=$value"
}

class SystemSetting<T>(
    override val path: List<String>,
    private val deserializer: DeserializationStrategy<T>,
    private val stringFormat: StringFormat,
) : Setting<T> {
    override val origin: String = "sys"
    override val value: T? get() = sysPropValue(path)?.let { stringFormat.decodeFromString(deserializer, it) }
    override fun toString(): String = "$origin:${path.joinToString(".")}=$value"
}

private fun envValue(path: List<String>): String? = System.getenv(environmentKey(path))
private fun sysPropKey(path: List<String>): String = path.joinToString(".")
private fun sysPropValue(path: List<String>): String? = System.getProperty(sysPropKey(path))
