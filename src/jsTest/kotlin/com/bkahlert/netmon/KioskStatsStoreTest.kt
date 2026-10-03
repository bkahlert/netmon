package com.bkahlert.netmon

import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class KioskStatsStoreTest {

    @Test
    fun holds_the_latest_sample_and_drops_it_when_a_poll_fails() = runTest {
        val polls = Channel<KioskStats?>()
        val store = KioskStatsStore(interval = 1.milliseconds, load = { polls.receive() }, clock = { Instant.fromEpochSeconds(1_006) }, job = job)

        polls.send(sample(at = 1_000))
        delay(10.milliseconds)
        store.current shouldBe sample(at = 1_000)
        polls.send(sample(at = 1_005))
        delay(10.milliseconds)
        store.current shouldBe sample(at = 1_005)
        polls.send(null)
        delay(10.milliseconds)

        store.current shouldBe null
    }

    @Test
    fun drops_a_sample_older_than_three_intervals() = runTest {
        val polls = Channel<KioskStats?>()
        val store = KioskStatsStore(interval = 1.milliseconds, load = { polls.receive() }, clock = { Instant.fromEpochSeconds(1_015) }, job = job)

        polls.send(sample(at = 1_000))
        delay(10.milliseconds)

        store.current shouldBe null
    }
}

private fun sample(at: Long): KioskStats = KioskStats(at = at, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
