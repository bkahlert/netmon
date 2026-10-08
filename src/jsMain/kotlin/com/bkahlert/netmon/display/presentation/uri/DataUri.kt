package com.bkahlert.netmon.display.presentation.uri

import kotlin.io.encoding.Base64

/** A `data:` URI ([RFC 2397](https://www.rfc-editor.org/rfc/rfc2397)) with its [mediaType] and decoded [data]. */
class DataUri(
    val mediaType: String?,
    val data: ByteArray,
) : Uri("$SCHEME${mediaType.orEmpty()};base64,${Base64.encode(data)}") {

    /** Whether the data is an SVG image. */
    val isSvg: Boolean get() = mediaType?.substringBefore(';') == SVG

    companion object {
        const val SCHEME: String = "data:"
        const val SVG: String = "image/svg+xml"

        // Groups are read by index: Kotlin/JS 1.9 has no named-group lookup; the names document the structure.
        private val regex = Regex("^data:(?<mediaType>[^,;]+(?:;[^,;=]+=[^,;]+)*)?(?<base64>;base64)?,(?<data>[\\s\\S]*)$")

        /** A data URI carrying the given [svg] markup. */
        fun svg(svg: String): DataUri = DataUri(SVG, svg.encodeToByteArray())

        /** Parses the given data URI [text]. */
        fun parse(text: String): DataUri {
            val match = requireNotNull(regex.matchEntire(text)) { "$text is no valid data URI" }
            val (mediaType, base64, payload) = match.destructured
            return DataUri(
                mediaType = mediaType.takeIf { it.isNotEmpty() },
                data = if (base64.isNotEmpty()) Base64.decode(payload) else decodeURIComponent(payload).encodeToByteArray(),
            )
        }
    }
}

private external fun decodeURIComponent(encoded: String): String
