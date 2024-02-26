package com.bkahlert.netmon.ui

import com.bkahlert.kommons.js.console
import com.bkahlert.netmon.fritz2.observedMutations
import com.bkahlert.netmon.fritz2.verticalScrollCoverageRatio
import dev.fritz2.core.Tag
import dev.fritz2.core.WithDomNode
import dev.fritz2.core.asElementList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserverInit
import org.w3c.dom.css.CSSStyleDeclaration
import kotlin.math.sqrt

/** The zoom factor of the [Tag], or `null` if not set. */
private inline var Tag<HTMLElement>.zoom: Double?
    get() = domNode.zoom
    set(value) = kotlin.run { domNode.zoom = value }

/** The zoom factor of the [HTMLElement], or `null` if not set. */
private inline var HTMLElement.zoom: Double?
    get() = style.zoom
    set(value) = kotlin.run { style.zoom = value }

/** The zoom factor property of the [CSSStyleDeclaration], or `null` if not set. */
private inline var CSSStyleDeclaration.zoom: Double?
    get() = getPropertyValue("zoom").takeUnless { it.isEmpty() }?.toDoubleOrNull()
    set(value) = setProperty("zoom", value?.toString().orEmpty())

/**
 * Eliminates the need for horizontal scrolling respectively
 * avoids clipping by reducing the zoom factor just enough
 * to fit the content into the available space.
 *
 * ***Note:** this function is one shot.*
 */
fun Tag<HTMLElement>.zoomToFitClientWidth(): Int? = domNode.zoomToFitClientWidth()

/**
 * Eliminates the need for horizontal scrolling respectively
 * avoids clipping by reducing the zoom factor just enough
 * to fit the content into the available space.
 *
 * ***Note:** this function is one shot.*
 *
 * @return the request id returned by [org.w3c.dom.Window.requestAnimationFrame]
 */
fun HTMLElement.zoomToFitClientWidth(): Int? = ownerDocument?.defaultView?.let { window ->
    window.requestAnimationFrame {
        val clientWidth = clientWidth
        val scrollWidth = scrollWidth
        if (scrollWidth > clientWidth) {
            val decreasedZoom = clientWidth.toDouble() / scrollWidth
            zoom = decreasedZoom
        }
    }
}

/**
 * Flow of zoom factors that fit the content into the available space.
 *
 * **Important:** Requires [HTMLElement] to be styled with `overflow-y: hidden`.
 */
private val HTMLElement.zoomsToFitClientHeight: Flow<Double>
    get() = observedMutations(MutationObserverInit(childList = true, subtree = true, attributes = false, characterData = false))
        .conflate()
        .map { verticalScrollCoverageRatio }
        .distinctUntilChanged()
        .map { coverageRatio ->
            val zoom = zoom ?: 1.0
            when {
                // square because changing the zoom factor changes width and height
                coverageRatio < 0.9 -> sqrt(coverageRatio) * zoom
                coverageRatio < 1.0 -> 0.95 * zoom
                else -> zoom
            }
        }
        .distinctUntilChanged()

/**
 * Eliminates the need for vertical scrolling by decreasing the zoom factor iteratively
 * whenever the content changes until the content fits into the available vertical space.
 *
 * ***Important:** zooming back in requires [resetZoomed] to be called
 * on an ancestor [Element] when space becomes available again.*
 */
fun Tag<HTMLElement>.zoomedToFitClientHeight() {
    domNode.ownerDocument?.defaultView?.let { window ->
        domNode.zoomsToFitClientHeight handledBy { zoomToFitClientHeight ->
            window.requestAnimationFrame {
                domNode.setAttribute("data-zooming", zoomToFitClientHeight.toString())
                window.requestAnimationFrame {
                    markZoomed(zoomToFitClientHeight)
                    console.debug("Setting zoom to %f of %o", zoomToFitClientHeight, domNode)
                    zoom = zoomToFitClientHeight
                    domNode.removeAttribute("data-zooming")
                }
            }
        }
    }
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
fun WithDomNode<Element>.resetZoomed(): List<HTMLElement> = domNode.querySelectorAll("[data-zoomed]").asElementList().onEach {
    it.removeAttribute("data-zoomed")
    it.zoom = null
}
