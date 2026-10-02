package com.bkahlert.kommons.browser

import com.bkahlert.netmon.uri.Uri
import com.bkahlert.netmon.uri.toUriOrNull
import org.w3c.dom.Element
import org.w3c.dom.HTMLScriptElement
import org.w3c.dom.Window
import org.w3c.dom.asList
import org.w3c.fetch.NO_CACHE
import org.w3c.fetch.RequestCache
import kotlin.js.Promise
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
    interval: Duration = INTERVAL,
) {

    init {
        window.setInterval(::refresh, interval.inWholeMilliseconds.toInt())
    }

    fun refresh() {
        uri.getEtagOrNull(window = window)
            .then {
                if (it != null) {
                    val prevEtag = etag
                    if (prevEtag == null) {
                        etag = it
                    } else if (prevEtag != it) {
                        window.location.reload()
                    }
                }
            }
    }

    companion object {
        /** Once a minute: enough for a new deploy to reach the panel. */
        val INTERVAL = 1.minutes

        fun Uri.getEtagOrNull(window: Window): Promise<String?> =
            fetch(method = "head", cache = RequestCache.NO_CACHE, window = window)
                .then(onFulfilled = { it.headers.get("etag") }, onRejected = { null })
    }
}
