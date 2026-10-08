package com.bkahlert.netmon.display.presentation.uri

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmInline

/**
 * A URL with [the "data" URL scheme](https://www.rfc-editor.org/rfc/rfc2397).
 */
@Serializable
@JvmInline
value class DataUrl(val url: String) : CharSequence by url {
    val mediaType: String get() = url.substringAfter("data:").substringBefore(",").removeSuffix(";base64")
    val base64: Boolean get() = url.contains(";base64")
    val data: String get() = url.substringAfter(",")

    constructor(
        mediaType: String,
        text: String,
    ) : this("""data:$mediaType,${text.encodeURLPath(encodeSlash = true)}""")


    constructor(
        mediaType: String,
        bytes: ByteArray,
    ) : this("""data:$mediaType;base64,${Base64.encode(bytes)}""")

    override fun toString(): String = url
}

private fun String.encodeURLPath(encodeSlash: Boolean): String = buildString {
    var index = 0
    while (index < this@encodeURLPath.length) {
        val current = this@encodeURLPath[index]
        if ((!encodeSlash && current == '/') || current in URL_ALPHABET_CHARS || current in VALID_PATH_PART) {
            append(current)
            index++
            continue
        }

        if (current == '%' &&
            index + 2 < this@encodeURLPath.length &&
            this@encodeURLPath[index + 1] in HEX_ALPHABET &&
            this@encodeURLPath[index + 2] in HEX_ALPHABET
        ) {
            append(current)
            append(this@encodeURLPath[index + 1])
            append(this@encodeURLPath[index + 2])

            index += 3
            continue
        }

        val symbolSize = if (current.isSurrogate()) 2 else 1
        this@encodeURLPath.encodeToByteArray(index, index + symbolSize).forEach {
            append(it.percentEncode())
        }
        index += symbolSize
    }
}

private val URL_ALPHABET_CHARS: Set<Char> = ((('a'..'z') + ('A'..'Z') + ('0'..'9'))).toSet()
private val HEX_ALPHABET: Set<Char> = (('a'..'f') + ('A'..'F') + ('0'..'9')).toSet()

/** from `pchar` in https://tools.ietf.org/html/rfc3986#section-2 */
private val VALID_PATH_PART = setOf(
    ':', '@',
    '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
    '-', '.', '_', '~'
)

private fun Byte.percentEncode(): String {
    val code = toInt() and 0xff
    val array = CharArray(3)
    array[0] = '%'
    array[1] = hexDigitToChar(code shr 4)
    array[2] = hexDigitToChar(code and 0xf)
    return array.concatToString()
}

private fun hexDigitToChar(digit: Int): Char = when (digit) {
    in 0..9 -> '0' + digit
    else -> 'A' + digit - 10
}
