package com.bkahlert.netmon.display.support

import com.bkahlert.netmon.display.support.fritz2.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CurrentTimeStoreTest {

    @Test
    fun ticks_at_its_interval() = runTest {
        val clock = MutableClock(Instant.fromEpochSeconds(1_000))
        val store = CurrentTimeStore(1.milliseconds, clock, job)
        val next = Instant.fromEpochSeconds(1_001)
        clock.current = next

        withTimeout(1.seconds) { store.data.first { it == next } } shouldBe next
    }
}

private class MutableClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}
