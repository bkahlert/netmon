package com.bkahlert.kommons.config

import com.bkahlert.kommons.uri.Uri
import com.bkahlert.kommons.uri.queryParameters
import com.bkahlert.kommons.uri.toUri
import kotlinx.browser.window
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat

internal actual fun <T> settingValue(
    path: List<String>,
    deserializer: DeserializationStrategy<T>,
    stringFormat: StringFormat,
): Setting<T> = UriSetting(path, deserializer, stringFormat)

class UriSetting<T>(
    override val path: List<String>,
    private val deserializer: DeserializationStrategy<T>,
    private val stringFormat: StringFormat,
) : Setting<T> {
    override val origin: String = "uri"
    override val value: T?
        get() = path.joinToString(".")
            .let { UriSource.uri.queryParameters[it] }
            ?.let { stringFormat.decodeFromString(deserializer, it) }

    override fun toString(): String = "$origin:${path.joinToString(".")}=$value"
}

internal object UriSource {
    var testUri: Uri? = null
    val uri: Uri get() = testUri ?: window.location.href.toUri()
}
