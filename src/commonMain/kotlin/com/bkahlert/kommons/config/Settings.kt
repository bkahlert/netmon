package com.bkahlert.kommons.config

import com.bkahlert.netmon.serialization.JsonFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import kotlin.properties.ReadOnlyProperty

open class Settings private constructor(
    val name: String?,
    val parent: Settings?,
) {
    constructor(name: String? = null) : this(name, null)

    val path: List<String> by lazy {
        buildList {
            var instance: Settings? = this@Settings
            while (instance != null) {
                instance.name?.also { add(it) }
                instance = instance.parent
            }
        }
    }

    open inner class Group(name: String) : Settings(name, this@Settings)
}

interface Setting<out T> : ReadOnlyProperty<Settings?, T>

expect fun <T, D : T> setting(
    default: D,
    deserializer: DeserializationStrategy<T>,
    stringFormat: StringFormat = JsonFormat,
    name: String? = null,
): Setting<T>

inline fun <reified T, D : T> setting(
    default: D,
    stringFormat: StringFormat = JsonFormat,
    name: String? = null,
): Setting<T> = setting(
    default, when (T::class) {
        String::class -> serializer<T>()
        else -> serializer<T>()
    }, stringFormat, name
)

fun <T> setting(
    deserializer: DeserializationStrategy<T>,
    stringFormat: StringFormat = JsonFormat,
    name: String? = null,
): Setting<T?> = setting(null, deserializer, stringFormat, name)

inline fun <reified T> setting(
    name: String? = null,
    stringFormat: StringFormat = JsonFormat,
): Setting<T?> = setting(null, stringFormat, name)
