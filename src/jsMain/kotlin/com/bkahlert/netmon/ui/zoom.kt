package com.bkahlert.netmon.ui

import com.bkahlert.netmon.fritz2.observedMutations
import com.bkahlert.netmon.fritz2.verticalScrollCoverageRatio
import dev.fritz2.core.Tag
import dev.fritz2.core.WithDomNode
import dev.fritz2.core.asElementList
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserverInit
import kotlin.math.sqrt

/**
 * Guarantees no vertical scrollbars by zooming out iteratively
 * whenever the content changes.
 *
 * ***Important:** zooming back in requires [resetZoomed] to be called
 * on an ancestor [Element] when space becomes available again.*
 */
fun Tag<HTMLElement>.zoomOut() {
    className("overflow-y-hidden")
    inlineStyle(
        observedMutations(MutationObserverInit(childList = true, subtree = true, attributes = false, characterData = false))
            .conflate()
            .map { domNode.verticalScrollCoverageRatio }
            .distinctUntilChanged()
            .map { coverageRatio ->
                val zoom = domNode.style.getPropertyValue("zoom").toDoubleOrNull() ?: 1.0
                when {
                    // square because changing the zoom factor changes width and height
                    coverageRatio < 0.9 -> sqrt(coverageRatio) * zoom
                    coverageRatio < 1.0 -> 0.95 * zoom
                    else -> zoom
                }
            }
            .distinctUntilChanged()
            .onEach { markZoomed(it) }
            .map { "zoom: $it" })
}

/**
 * Marks the element as zoomed if [zoom] is not `1.0`, in order
 * to be resettable by [resetZoomed].
 */
fun WithDomNode<Element>.markZoomed(zoom: Double) {
    if (zoom != 1.0) domNode.setAttribute("data-zoomed", "")
    else domNode.removeAttribute("data-zoomed")
}

/**
 * Resets the zoom of all zoomed elements marked by [markZoomed] to `1.0`.
 */
fun WithDomNode<Element>.resetZoomed() {
    domNode.querySelectorAll("[data-zoomed]").asElementList().forEach {
        it.removeAttribute("data-zoomed")
        it.style.setProperty("zoom", "1")
    }
}
