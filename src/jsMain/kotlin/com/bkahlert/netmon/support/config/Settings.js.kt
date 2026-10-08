package com.bkahlert.netmon.support.config

import kotlinx.browser.window
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat
import org.w3c.dom.url.URLSearchParams

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
            .let { UriSource.query.get(it) }
            ?.let { stringFormat.decodeFromString(deserializer, it) }

    override fun toString(): String = "$origin:${path.joinToString(".")}=$value"
}

internal object UriSource {
    var testQuery: String? = null
    val query: URLSearchParams get() = URLSearchParams(testQuery ?: window.location.search)
}
