package com.bkahlert.kommons.browser

import com.bkahlert.netmon.uri.toUriOrNull
import io.kotest.matchers.collections.shouldContainExactly
import org.w3c.dom.Window
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class AutoRefresherTest {

    @Test
    fun polls_at_the_given_interval() {
        val delays = mutableListOf<Int>()

        AutoRefresher(uri = "http://localhost/netmon.js".toUriOrNull()!!, window = windowRecording(delays), interval = 50.milliseconds)

        delays.shouldContainExactly(50)
    }

    @Test
    fun polls_once_a_minute_by_default() {
        val delays = mutableListOf<Int>()

        AutoRefresher(uri = "http://localhost/netmon.js".toUriOrNull()!!, window = windowRecording(delays))

        delays.shouldContainExactly(60_000)
    }
}

private fun windowRecording(delays: MutableList<Int>): Window {
    val window = js("({})")
    window.setInterval = { _: dynamic, delay: Int -> delays.add(delay); 1 }
    return window.unsafeCast<Window>()
}
