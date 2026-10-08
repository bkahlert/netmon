package com.bkahlert.netmon.display.support

import com.bkahlert.netmon.display.support.fritz2.runTest
import io.kotest.matchers.shouldBe
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class OwnedRenderTest {

    @Test
    fun failed_mount_clears_partial_content_after_the_render_job_finishes() = runTest {
        val target = target()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        var renderJob: kotlinx.coroutines.Job? = null
        val mounting = CoroutineScope(coroutineContext).async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                renderOwned(target) {
                    renderJob = job
                    target.appendChild(document.createElement("span").also { it.textContent = "partial" })
                    CoroutineScope(job).launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            cleanupStarted.complete(Unit)
                            allowCleanup.await()
                        }
                    }
                    error("mount failed")
                }
            }
        }

        try {
            withTimeout(1.seconds) { cleanupStarted.await() }
            mounting.isCompleted shouldBe false

            allowCleanup.complete(Unit)
            val result = withTimeout(1.seconds) { mounting.await() }
            result.exceptionOrNull()?.message shouldBe "mount failed"
            target.childNodes.length shouldBe 0
            renderJob?.isCompleted shouldBe true
        } finally {
            allowCleanup.complete(Unit)
            target.remove()
        }
    }
}

private fun target(): HTMLElement =
    (document.createElement("div") as HTMLElement).also { document.body?.appendChild(it) }
