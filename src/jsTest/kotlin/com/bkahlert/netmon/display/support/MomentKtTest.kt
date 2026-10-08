package com.bkahlert.netmon.display.support

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MomentKtTest {

    @Test
    fun descriptive() = runTest {
        forAll(
            row(Duration.ZERO, "now"),
            row(200.milliseconds, "now"),
            row(30.seconds, "in 30s"),
            row(-(30.seconds), "30s ago"),
            row(5.minutes, "in 5m"),
            row(2.hours, "in 2h"),
            row(2.hours + 30.minutes, "in 2h 30m"),
            row(-(2.hours + 30.minutes), "2h 30m ago"),
            row(12.hours, "in 12h"),
            row(23.hours + 45.minutes, "in 1d"),
            row(3.days + 4.hours, "in 3d 4h"),
            row(3.days, "in 3d"),
            row(10.days, "in 10d"),
        ) { duration, expected ->
            duration.toMomentString() shouldBe expected
        }
    }

    @Test
    fun not_descriptive() = runTest {
        forAll(
            row(30.seconds, "30s"),
            row(-(30.seconds), "-30s"),
            row(-(2.hours + 30.minutes), "-2h 30m"),
        ) { duration, expected ->
            duration.toMomentString(descriptive = false) shouldBe expected
        }
    }
}
