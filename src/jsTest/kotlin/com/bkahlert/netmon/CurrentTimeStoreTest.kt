package com.bkahlert.netmon

import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class CurrentTimeStoreTest {

    @Test
    fun ticks_at_its_interval() = runTest {
        val store = CurrentTimeStore(refreshInterval = 50.milliseconds)
        val initial = store.current

        delay(200)

        store.current shouldBeGreaterThan initial
    }

    @Test
    fun the_minute_clock_ticks_once_a_minute() {
        MinuteClock.refreshInterval shouldBe 1.minutes
    }
}
