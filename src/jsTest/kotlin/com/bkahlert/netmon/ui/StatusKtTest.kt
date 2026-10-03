package com.bkahlert.netmon.ui

import com.bkahlert.netmon.ConsoleLogStore
import com.bkahlert.netmon.KioskStats
import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

class StatusKtTest {

    @Test
    fun shows_the_kiosks_sample_as_pills_between_the_title_and_the_start() = runTest {
        val container = rendered { status(ConsoleLogStore("info" to "Starting..."), MutableStateFlow(SAMPLE)) }

        val text = container.textOnce("kiosk 161 MB")

        text shouldContain "web 114 %"
        text shouldContain "kiosk 118 %"
        text.indexOf("web 114 %") shouldBeGreaterThan text.indexOf("Network Monitor")
        text.indexOf("kiosk 161 MB") shouldBeLessThan text.indexOf("started")
        container.remove()
    }

    @Test
    fun shows_no_pills_without_a_sample() = runTest {
        val container = rendered { status(ConsoleLogStore("info" to "Starting..."), MutableStateFlow(null)) }

        val text = container.textOnce("Starting...")

        text shouldNotContain "%"
        text shouldNotContain "MB"
        container.remove()
    }

    @Test
    fun leaves_out_the_pill_of_an_absent_source() = runTest {
        val container = rendered { status(ConsoleLogStore("info" to "Starting..."), MutableStateFlow(SAMPLE.copy(webCpu = null))) }

        val text = container.textOnce("kiosk 118 %")

        text shouldNotContain "web"
        container.remove()
    }
}

private val SAMPLE = KioskStats(at = 1_000, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
