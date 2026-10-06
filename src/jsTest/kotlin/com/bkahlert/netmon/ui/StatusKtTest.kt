package com.bkahlert.netmon.ui

import com.bkahlert.netmon.BUILD_VERSION
import com.bkahlert.netmon.ConsoleLogStore
import com.bkahlert.netmon.KioskStats
import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.time.Clock

class StatusKtTest {

    @Test
    fun shows_the_kiosks_sample_as_pills_between_the_title_and_the_start() = runTest {
        val now = Clock.System.now()
        val container = rendered { status(ConsoleLogStore("info" to "Starting...", job), MutableStateFlow(SAMPLE), MutableStateFlow(now), now) }

        val text = container.textOnce("kiosk 161 MB")

        text shouldContain "web 114 %"
        text shouldContain "kiosk 118 %"
        text.indexOf("web 114 %") shouldBeGreaterThan text.indexOf("Network Monitor")
        text.indexOf("kiosk 161 MB") shouldBeLessThan text.indexOf("started")
        container.remove()
    }

    @Test
    fun shows_the_build_version_next_to_the_title() = runTest {
        val now = Clock.System.now()
        val container = rendered {
            status(ConsoleLogStore("info" to "Starting...", job), MutableStateFlow(SAMPLE), MutableStateFlow(now), now, version = "v2.2.0-4-g54adb6b")
        }

        val text = container.textOnce("kiosk 161 MB")

        text shouldContain "v2.2.0-4-g54adb6b"
        text.indexOf("v2.2.0-4-g54adb6b") shouldBeGreaterThan text.indexOf("Network Monitor")
        text.indexOf("v2.2.0-4-g54adb6b") shouldBeLessThan text.indexOf("web 114 %")
        container.remove()
    }

    @Test
    fun shows_the_version_baked_in_at_build_time_by_default() = runTest {
        val now = Clock.System.now()
        val container = rendered { status(ConsoleLogStore("info" to "Starting...", job), MutableStateFlow(null), MutableStateFlow(now), now) }

        val text = container.textOnce("Starting...")

        text shouldContain BUILD_VERSION
        BUILD_VERSION shouldNotBe "unknown"
        container.remove()
    }

    @Test
    fun shows_no_pills_without_a_sample() = runTest {
        val now = Clock.System.now()
        val container = rendered { status(ConsoleLogStore("info" to "Starting...", job), MutableStateFlow(null), MutableStateFlow(now), now) }

        val text = container.textOnce("Starting...")

        text shouldNotContain "%"
        text shouldNotContain "MB"
        container.remove()
    }

    @Test
    fun leaves_out_the_pill_of_an_absent_source() = runTest {
        val now = Clock.System.now()
        val container = rendered {
            status(ConsoleLogStore("info" to "Starting...", job), MutableStateFlow(SAMPLE.copy(webCpu = null)), MutableStateFlow(now), now)
        }

        val text = container.textOnce("kiosk 118 %")

        text shouldNotContain "web"
        container.remove()
    }
}

private val SAMPLE = KioskStats(at = 1_000, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
