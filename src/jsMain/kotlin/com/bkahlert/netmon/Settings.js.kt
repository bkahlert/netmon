package com.bkahlert.netmon

import com.bkahlert.kommons.uri.Uri
import com.bkahlert.kommons.uri.queryParameters
import com.bkahlert.kommons.uri.toUri
import kotlinx.browser.window
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat
import kotlin.reflect.KProperty

actual fun <T, D : T> setting(
    default: D,
    deserializer: DeserializationStrategy<T>,
    stringFormat: StringFormat,
    name: String?,
): Setting<T> = UriSetting(name, default) { stringFormat.decodeFromString(deserializer, it) }

private class UriSetting<T, D : T>(
    val name: String?,
    val default: D,
    val parse: (String) -> T,
) : Setting<T> {

    override fun getValue(thisRef: Settings?, property: KProperty<*>): T = sequence {
        var parent = thisRef
        while (parent != null) {
            parent.name?.also { yield(it) }
            parent = parent.parent
        }
        yield(name ?: property.name)
    }.joinToString(".") {
        it
    }.let { propertyName ->
        UriSource.uri.queryParameters[propertyName]?.let { parse(it) } ?: default
    }
}

internal object UriSource {
    var testUri: Uri? = null
    val uri: Uri get() = testUri ?: window.location.href.toUri()
}
