package com.bkahlert.kommons.config

import com.bkahlert.kommons.serialization.UnquotedStringsFormat
import com.bkahlert.netmon.serialization.JsonFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import kotlin.jvm.JvmStatic
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

open class Settings private constructor(
    val name: String?,
    val parent: Settings?,
) {
    constructor(name: String? = null) : this(name, null)

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
        stringFormat: StringFormat = UnquotedStringsFormat(JsonFormat),
        name: String? = null,
    ): SettingDelegateProvider<T> = SettingDelegateProvider { thisRef, property ->
        val setting = settingValue(
            path = thisRef?._path.orEmpty().plus(name ?: property.name),
            deserializer = deserializer,
            stringFormat = stringFormat,
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
            stringFormat: StringFormat = UnquotedStringsFormat(JsonFormat),
            name: String? = null,
        ): SettingDelegateProvider<T> = setting(
            default, when (T::class) {
                String::class -> serializer<T>()
                else -> serializer<T>()
            }, stringFormat, name
        )

        @Suppress("NOTHING_TO_INLINE")
        @JvmStatic
        protected inline fun <T> Settings.setting(
            deserializer: DeserializationStrategy<T>,
            stringFormat: StringFormat = UnquotedStringsFormat(JsonFormat),
            name: String? = null,
        ): SettingDelegateProvider<T?> = setting(null, deserializer, stringFormat, name)

        @JvmStatic
        protected inline fun <reified T> Settings.setting(
            name: String? = null,
            stringFormat: StringFormat = UnquotedStringsFormat(JsonFormat),
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
    stringFormat: StringFormat = UnquotedStringsFormat(JsonFormat),
): Setting<T>
