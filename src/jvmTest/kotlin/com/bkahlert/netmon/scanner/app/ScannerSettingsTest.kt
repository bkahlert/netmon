package com.bkahlert.netmon.scanner.app

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class ScannerSettingsTest {

    @Test
    fun down_after_defaults_to_three_minutes() {
        ScannerSettings.downAfter shouldBe 3.minutes
    }

    @Test
    fun down_after_reads_an_iso_8601_duration() = withSystemProperty("scanner.downAfter", "PT5M") {
        ScannerSettings.downAfter shouldBe 5.minutes
    }
}

private fun withSystemProperty(key: String, value: String, block: () -> Unit) {
    val previous = System.getProperty(key)
    System.setProperty(key, value)
    try {
        block()
    } finally {
        if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
    }
}
