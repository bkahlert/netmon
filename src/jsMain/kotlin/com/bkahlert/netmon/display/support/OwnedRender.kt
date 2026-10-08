package com.bkahlert.netmon.display.support

import dev.fritz2.core.RenderContext
import dev.fritz2.core.render
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.w3c.dom.HTMLElement
import kotlin.coroutines.resume

internal suspend fun renderOwned(
    target: HTMLElement,
    content: RenderContext.() -> Unit,
): OwnedRender = suspendCancellableCoroutine { continuation ->
    var renderJob: Job? = null
    continuation.invokeOnCancellation { renderJob?.cancel() }
    render(target) {
        val callbackJob = job
        renderJob = callbackJob
        if (!continuation.isActive) {
            callbackJob.cancel()
        } else {
            try {
                content()
                continuation.resume(OwnedRender(target, callbackJob))
            } catch (failure: Throwable) {
                callbackJob.invokeOnCompletion {
                    target.innerHTML = ""
                    continuation.resumeWith(Result.failure(failure))
                }
                callbackJob.cancel()
            }
        }
    }
}

internal class OwnedRender(
    private val target: HTMLElement,
    private val job: Job,
) {
    private var disposed = false

    suspend fun dispose() {
        if (disposed) return
        disposed = true
        withContext(NonCancellable) {
            job.cancelAndJoin()
            target.innerHTML = ""
        }
    }
}
