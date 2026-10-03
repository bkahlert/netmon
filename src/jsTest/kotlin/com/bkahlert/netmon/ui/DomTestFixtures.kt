package com.bkahlert.netmon.ui

import dev.fritz2.core.RenderContext
import dev.fritz2.core.render
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun rendered(content: RenderContext.() -> Unit): HTMLElement {
    val container = document.createElement("div") as HTMLElement
    document.body?.appendChild(container)
    render(container) { content() }
    return container
}

suspend fun HTMLElement.textOnce(part: String, timeout: Duration = 2.seconds): String {
    withTimeoutOrNull(timeout) {
        suspendCancellableCoroutine { continuation ->
            val observer = MutationObserver { _, observer ->
                if (continuation.isActive && textContent.orEmpty().contains(part)) {
                    observer.disconnect()
                    continuation.resume(Unit)
                }
            }
            observer.observe(this@textOnce, MutationObserverInit(childList = true, subtree = true, characterData = true))
            continuation.invokeOnCancellation { observer.disconnect() }
            if (textContent.orEmpty().contains(part)) {
                observer.disconnect()
                continuation.resume(Unit)
            }
        }
    }
    return textContent.orEmpty()
}
