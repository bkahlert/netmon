package com.bkahlert.netmon.display.app

import com.bkahlert.netmon.display.support.OwnedRender
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext

/** A mounted display and the resources it owns. */
class DisplayApp internal constructor(
    private val job: Job,
    private val renders: List<OwnedRender>,
    private val onDispose: () -> Unit,
) {
    private var disposed = false

    /** Stops the display's rendering and subscriptions, then clears its render targets. */
    suspend fun dispose() {
        if (disposed) return
        disposed = true
        withContext(NonCancellable) {
            try {
                renders.asReversed().forEach { it.dispose() }
            } finally {
                try {
                    onDispose()
                } finally {
                    job.cancelAndJoin()
                }
            }
        }
    }
}
