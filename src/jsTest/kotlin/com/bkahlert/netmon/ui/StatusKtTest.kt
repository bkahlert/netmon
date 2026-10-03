package com.bkahlert.netmon.ui

import com.bkahlert.netmon.ConsoleLogStore
import com.bkahlert.netmon.KioskStats
import com.bkahlert.netmon.fritz2.runTest
import dev.fritz2.core.render
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class StatusKtTest {

    @Test
    fun shows_the_kiosks_sample_as_pills_between_the_title_and_the_start() = runTest {
        val text = statusText(KioskStats(at = 1_000, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736))

        text shouldContain "web 114 %"
        text shouldContain "kiosk 118 %"
        text shouldContain "kiosk 161 MB"
        text.indexOf("web 114 %") shouldBeGreaterThan text.indexOf("Network Monitor")
        text.indexOf("kiosk 161 MB") shouldBeLessThan text.indexOf("started")
    }

    @Test
    fun shows_no_pills_without_a_sample() = runTest {
        val text = statusText(null)

        text shouldNotContain "%"
        text shouldNotContain "MB"
    }

    @Test
    fun leaves_out_the_pill_of_an_absent_source() = runTest {
        val text = statusText(KioskStats(at = 1_000, interval = 5, kioskCpu = 118, kioskMemory = 168_820_736))

        text shouldNotContain "web"
        text shouldContain "kiosk 118 %"
    }
}

private suspend fun statusText(stats: KioskStats?): String {
    val container = document.createElement("div") as HTMLElement
    document.body?.appendChild(container)
    render(container) { status(ConsoleLogStore("info" to "Starting..."), MutableStateFlow(stats)) }
    delay(50.milliseconds)
    val text = container.textContent.orEmpty()
    container.remove()
    return text
}
