package com.bkahlert.kommons.browser

import com.bkahlert.netmon.uri.Uri
import com.bkahlert.netmon.uri.toUriOrNull
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Window
import org.w3c.fetch.Headers
import org.w3c.fetch.Response
import org.w3c.fetch.ResponseInit
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class AutoRefresherTest {

    @Test
    fun polls_at_the_given_interval() {
        val delays = mutableListOf<Int>()

        AutoRefresher(uri = SCRIPT, window = windowRecording(delays), interval = 50.milliseconds)

        delays.shouldContainExactly(50)
    }

    @Test
    fun polls_once_a_minute_by_default() {
        val delays = mutableListOf<Int>()

        AutoRefresher(uri = SCRIPT, window = windowRecording(delays))

        delays.shouldContainExactly(60_000)
    }

    @Test
    fun keeps_the_etag_of_the_first_answer() = runTest {
        val page = PageWindow("\"1\"", "\"1\"")
        val refresher = AutoRefresher(uri = SCRIPT, window = page.window)

        refresher.refresh().await()
        refresher.refresh().await()

        refresher.etag shouldBe "\"1\""
        page.reloads shouldBe 0
    }

    @Test
    fun reloads_the_page_on_a_changed_etag() = runTest {
        val page = PageWindow("\"1\"", "\"2\"")
        val refresher = AutoRefresher(uri = SCRIPT, window = page.window)

        refresher.refresh().await()
        refresher.refresh().await()

        page.reloads shouldBe 1
    }

    @Test
    fun ignores_a_failed_poll() = runTest {
        val page = PageWindow("\"1\"", null, "\"2\"")
        val refresher = AutoRefresher(uri = SCRIPT, window = page.window)

        refresher.refresh().await()
        refresher.refresh().await()

        refresher.etag shouldBe "\"1\""
        page.reloads shouldBe 0
        refresher.refresh().await()
        page.reloads shouldBe 1
    }
}

private val SCRIPT: Uri = "http://localhost/netmon.js".toUriOrNull()!!

private fun windowRecording(delays: MutableList<Int>): Window {
    val window = js("({})")
    window.setInterval = { _: dynamic, delay: Int -> delays.add(delay); 1 }
    return window.unsafeCast<Window>()
}

private class PageWindow(vararg etags: String?) {
    var reloads = 0
    val window: Window

    init {
        val answers = etags.toMutableList()
        val window = js("({})")
        window.setInterval = { _: dynamic, _: Int -> 1 }
        window.location = js("({})")
        window.location.reload = { reloads++; Unit }
        window.fetch = { _: dynamic, init: dynamic ->
            val etag = answers.removeFirst()
            when {
                init.window != null -> js("Promise.reject(new TypeError('Window can only be null.'))") // the Fetch standard's rule
                etag == null -> js("Promise.reject(new TypeError('Load failed'))")
                else -> Promise.resolve(Response(null, ResponseInit(headers = Headers().apply { append("etag", etag) })))
            }
        }
        this.window = window.unsafeCast<Window>()
    }
}
