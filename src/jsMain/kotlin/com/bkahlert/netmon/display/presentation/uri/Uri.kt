package com.bkahlert.netmon.display.presentation.uri

import org.w3c.dom.url.URLSearchParams

/** A URI in its string form; a [DataUri] is the one kind whose content is at hand. */
open class Uri(val value: String) {

    /** The parameters of the query component, empty if there is none. */
    val queryParameters: URLSearchParams get() = URLSearchParams(value.substringAfter('?', "").substringBefore('#'))

    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is Uri && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

/** Parses this string as a [Uri]; `data:` URIs become [DataUri]s. */
fun String.toUri(): Uri = if (startsWith(DataUri.SCHEME)) DataUri.parse(this) else Uri(this)

/** Parses this string as a [Uri], or returns `null` if it is no valid URI. */
fun String.toUriOrNull(): Uri? = runCatching { toUri() }.getOrNull()
