package com.bkahlert.netmon.scanner

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class ScannerSettingsTest {

    @Test
    fun down_after_defaults_to_three_minutes() {
        ScannerSettings.downAfter shouldBe 3.minutes
    }
}
