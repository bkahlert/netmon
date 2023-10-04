package com.bkahlert.kommons.browser

import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.uri.Uri
import com.bkahlert.kommons.uri.toUriOrNull
import org.w3c.dom.Element
import org.w3c.dom.HTMLScriptElement
import org.w3c.dom.Window
import org.w3c.dom.asList
import org.w3c.fetch.NO_CACHE
import org.w3c.fetch.RequestCache
import kotlin.js.Promise
import kotlin.time.Duration.Companion.seconds

value class AutoRefreshers(val autoRefreshers: List<AutoRefresher>) : List<AutoRefresher> by autoRefreshers {

    constructor(
        vararg elements: Element?,
        window: Window = kotlinx.browser.window,
        extract: (Element) -> List<Uri> = ::extract,
    ) : this(elements
        .flatMap { it?.let(extract).orEmpty() }
        .map { AutoRefresher(window = window, uri = it) }
        .plus(AutoRefresher(window.location.href.toUriOrNull() ?: error("Cannot extract URI from ${window.location.href}")))
    )

    constructor(
        window: Window = kotlinx.browser.window,
        extract: (Element) -> List<Uri> = ::extract
    ) : this(
        window.document.head,
        window.document.body,
        window = window,
        extract = extract,
    )

    val uris: List<Uri> get() = autoRefreshers.map { it.uri }

    companion object {
        fun extract(element: Element): List<Uri> = element
            .getElementsByTagName("script")
            .asList()
            .filterIsInstance<HTMLScriptElement>()
            .mapNotNull { it.src.toUriOrNull() }
    }
}

class AutoRefresher(
    val uri: Uri,
    var etag: String? = null,
    val window: Window = kotlinx.browser.window,
) {

    init {
        window.setInterval(::refresh, INTERVAL.inWholeMilliseconds.toInt())
    }

    fun refresh() {
        console.debug("Checking for ETag for %s", uri)
        uri.getEtagOrNull(window = window)
            .then {
                if (it != null) {
                    val prevEtag = etag
                    if (prevEtag == null) {
                        etag = it
                    } else if (prevEtag != it) {
                        console.info("Etag changed for %s: %s -> %s", uri, prevEtag, it)
                        window.location.reload()
                    }
                }
            }
    }

    companion object {
        val INTERVAL = 5.seconds

        fun Uri.getEtagOrNull(window: Window): Promise<String?> =
            fetch(method = "head", cache = RequestCache.NO_CACHE, window = window)
                .then { it.headers.get("etag") }
    }
}
