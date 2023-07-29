@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui

import com.bkahlert.kommons.uri.DataUri
import com.bkahlert.kommons.uri.Uri
import dev.fritz2.core.RenderContext
import dev.fritz2.core.SvgTag
import dev.fritz2.core.mountSimple
import dev.fritz2.headless.foundation.Aria
import io.ktor.http.ContentType.Image
import kotlinx.browser.document
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.w3c.dom.Element
import org.w3c.dom.asList
import org.w3c.dom.svg.SVGElement

// TODO rename to image or svg (preferred)?

public fun RenderContext.icon(
    classes: String?,
    uri: Flow<Uri>,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    content: (SvgTag.() -> Unit)? = null,
): SvgTag = svg(classes) {
    mountSimple(job, uri.map { it.extractSvg(ignoredAttributes) }) { attributes(it.first); content(it.second) }
    content?.invoke(this)
}

public fun RenderContext.icon(
    uri: Flow<Uri>,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    content: (SvgTag.() -> Unit)? = null,
): SvgTag = icon(null, uri, ignoredAttributes, content)

public fun RenderContext.icon(
    classes: String?,
    uri: Uri,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    content: (SvgTag.() -> Unit)? = null,
): SvgTag = svg(classes) {
    uri.extractSvg(ignoredAttributes).run { attributes(first); content(second) }
    content?.invoke(this)
}

public fun RenderContext.icon(
    uri: Uri,
    ignoredAttributes: List<String> = listOf("role", "cursor"),
    content: (SvgTag.() -> Unit)? = null,
): SvgTag = icon(null, uri, ignoredAttributes, content)


private fun Uri.extractSvg(
    ignoredAttributes: List<String>,
): Pair<Map<String, String>, String> = when (val svgElement = toSvgElementOrNull()) {

    null -> mapOf(
        "xmlns:xlink" to "http://www.w3.org/1999/xlink",
        "viewBox" to "0 0 24 24",
        Aria.hidden to "true",
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
    is DataUri -> data.takeIf { mediaType?.match(Image.SVG) == true }?.toElement<SVGElement>()
    else -> null
}

private inline fun <reified T : Element> ByteArray.toElement(): T = document.createElement("div").run {
    innerHTML = decodeToString()
    firstElementChild as? T ?: error("Element is not of type ${T::class.simpleName}")
}
