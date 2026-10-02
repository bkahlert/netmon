@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui

import com.bkahlert.netmon.uri.DataUri
import com.bkahlert.netmon.uri.Uri
import dev.fritz2.core.RenderContext
import dev.fritz2.core.SvgTag
import dev.fritz2.core.mountSimple
import kotlinx.browser.document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.w3c.dom.Element
import org.w3c.dom.asList
import org.w3c.dom.svg.SVGElement

// TODO rename to image or svg (preferred)?

/**
 * Renders an image showing the contents of the given [uri] and the optional [classes].
 *
 * If the [uri] is a data URI with an SVG image, it's content is directly used (with [ignoredAttributes] applied).
 * Otherwise, the [uri] is used as the `xlink:href` attribute of an `image` element.
 *
 * The generated [SvgTag] can be further customized using the optional [content] lambda.
 */
public fun RenderContext.icon(
    classes: String?,
    uri: Flow<Uri>,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    content: (SvgTag.() -> Unit)? = null,
): SvgTag = svg(classes) {
    mountSimple(
        parentJob = job,
        upstream = uri
            .distinctUntilChanged()
            .map { it.extractSvg(ignoredAttributes) },
    ) { (attributes, content) ->
        attributes(attributes)
        content(content)
    }
    content?.invoke(this)
}

/**
 * Renders an image showing the contents of the given [uri].
 *
 * If the [uri] is a data URI with an SVG image, it's content is directly used (with [ignoredAttributes] applied).
 * Otherwise, the [uri] is used as the `xlink:href` attribute of an `image` element.
 *
 * The generated [SvgTag] can be further customized using the optional [content] lambda.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun RenderContext.icon(
    uri: Flow<Uri>,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    noinline content: (SvgTag.() -> Unit)? = null,
): SvgTag = icon(
    classes = null,
    uri = uri,
    ignoredAttributes = ignoredAttributes,
    content = content
)

/**
 * Renders an image showing the contents of the given [uri] and the optional [classes].
 *
 * If the [uri] is a data URI with an SVG image, it's content is directly used (with [ignoredAttributes] applied).
 * Otherwise, the [uri] is used as the `xlink:href` attribute of an `image` element.
 *
 * The generated [SvgTag] can be further customized using the optional [content] lambda.
 */
public fun RenderContext.icon(
    classes: String?,
    uri: Uri,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    content: (SvgTag.() -> Unit)? = null,
): SvgTag = svg(classes) {
    uri.extractSvg(ignoredAttributes).also { (attributes, content) ->
        attributes(attributes)
        content(content)
    }
    content?.invoke(this)
}

/**
 * Renders an image showing the contents of the given [uri].
 *
 * If the [uri] is a data URI with an SVG image, it's content is directly used (with [ignoredAttributes] applied).
 * Otherwise, the [uri] is used as the `xlink:href` attribute of an `image` element.
 *
 * The generated [SvgTag] can be further customized using the optional [content] lambda.
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun RenderContext.icon(
    uri: Uri,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    noinline content: (SvgTag.() -> Unit)? = null,
): SvgTag = icon(null, uri, ignoredAttributes, content)


private fun Uri.extractSvg(
    ignoredAttributes: List<String>,
): Pair<Map<String, String>, String> = when (val svgElement = toSvgElementOrNull()) {

    null -> mapOf(
        "xmlns:xlink" to "http://www.w3.org/1999/xlink",
        "viewBox" to "0 0 24 24",
        "aria-hidden" to "true",
    ) to """<image x="0" y="0" width="24" height="24" xlink:href="$this"/>"""

    else -> buildMap {
        svgElement.attributes.asList().forEach {
            if (it.name !in ignoredAttributes) {
                put(it.name, it.value)
            }
        }
    } to svgElement.innerHTML
}

private fun SvgTag.attributes(
    attributes: Map<String, String>,
) {
    attributes.forEach { (k, v) -> attr(k, v) }
}


private fun Uri.toSvgElementOrNull(): SVGElement? = when (this) {
    is DataUri -> data.takeIf { isSvg }?.toElement<SVGElement>()
    else -> null
}

private inline fun <reified T : Element> ByteArray.toElement(): T = document.createElement("div").run {
    innerHTML = decodeToString()
    firstElementChild as? T ?: error("Element is not of type ${T::class.simpleName}")
}
