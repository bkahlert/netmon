package com.bkahlert.netmon.display.support

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PulseKtTest {

    @Test
    fun pulses_for_ten_seconds_rests_and_turns_dated_after_two_minutes() = runTest {
        forAll(
            row(Duration.ZERO, "animate-variable-color"),
            row((-5).seconds, "animate-variable-color"),
            row(9.seconds, "animate-variable-color"),
            row(10.seconds, ""),
            row(119.seconds, ""),
            row(121.seconds, "text-yellow-500/60"),
        ) { since, expected ->
            radarClass(since) shouldBe expected
        }
    }
}
