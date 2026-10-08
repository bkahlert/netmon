package com.bkahlert.netmon.support.config

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import kotlin.jvm.JvmStatic
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

open class Settings private constructor(
    val name: String?,
    val parent: Settings?,
    @PublishedApi internal val defaultFormat: StringFormat,
) {
    constructor(name: String? = null, defaultFormat: StringFormat) : this(name, null, defaultFormat)

    private val _path: List<String> by lazy {
        buildList {
            var instance: Settings? = this@Settings
            while (instance != null) {
                instance.name?.also { add(it) }
                instance = instance.parent
            }
        }
    }

    protected val settings: MutableList<Pair<Setting<*>, Any?>> = mutableListOf()

    protected fun <T, D : T> setting(
        default: D,
        deserializer: DeserializationStrategy<T>,
        stringFormat: StringFormat? = null,
        name: String? = null,
    ): SettingDelegateProvider<T> = SettingDelegateProvider { thisRef, property ->
        val setting = settingValue(
            path = thisRef?._path.orEmpty().plus(name ?: property.name),
            deserializer = deserializer,
            stringFormat = stringFormat ?: defaultFormat,
        )
        settings.add(setting to default)
        ReadOnlyProperty { _, _ -> setting.value ?: default }
    }

    override fun toString(): String = buildString {
        append(this@Settings::class.simpleName ?: Settings::class.simpleName)
        append(settings.joinToString(prefix = "[", postfix = "]") { (setting, default) ->
            setting.value?.let { "${setting.origin}:${setting.name}=$it" } ?: "default:${setting.name}=$default"
        })
    }

    companion object {

        @JvmStatic
        protected inline fun <reified T, D : T> Settings.setting(
            default: D,
            stringFormat: StringFormat? = null,
            name: String? = null,
        ): SettingDelegateProvider<T> = setting(
            default,
            serializer<T>(),
            stringFormat ?: defaultFormat,
            name,
        )

        @Suppress("NOTHING_TO_INLINE")
        @JvmStatic
        protected inline fun <T> Settings.setting(
            deserializer: DeserializationStrategy<T>,
            stringFormat: StringFormat? = null,
            name: String? = null,
        ): SettingDelegateProvider<T?> = setting(null, deserializer, stringFormat ?: defaultFormat, name)

        @JvmStatic
        protected inline fun <reified T> Settings.setting(
            name: String? = null,
            stringFormat: StringFormat? = null,
        ): SettingDelegateProvider<T?> = setting(null, stringFormat, name)
    }
}

interface Setting<out T> {
    val origin: String
    val path: List<String>
    val name: String get() = path.last()
    val value: T?
}

fun interface SettingDelegateProvider<out T> : PropertyDelegateProvider<Settings?, ReadOnlyProperty<Settings?, T>>

internal expect fun <T> settingValue(
    path: List<String>,
    deserializer: DeserializationStrategy<T>,
    stringFormat: StringFormat,
): Setting<T>
