package com.bkahlert.netmon

import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class KioskStatsStoreTest {

    @Test
    fun holds_the_latest_sample_and_drops_it_when_a_poll_fails() = runTest {
        val polls = Channel<KioskStats?>()
        val store = KioskStatsStore(interval = 1.milliseconds, load = { polls.receive() }, clock = { Instant.fromEpochSeconds(1_006) }, job = job)

        polls.send(sample(at = 1_000))
        store.data.next { it != null } shouldBe sample(at = 1_000)
        polls.send(sample(at = 1_005))
        store.data.next { it != sample(at = 1_000) } shouldBe sample(at = 1_005)
        polls.send(null)

        store.data.next { it == null } shouldBe null
    }

    @Test
    fun polls_on_after_a_failed_poll() = runTest {
        val polls = Channel<KioskStats?>()
        val store = KioskStatsStore(interval = 1.milliseconds, load = { polls.receive() }, clock = { Instant.fromEpochSeconds(1_006) }, job = job)

        polls.send(null)
        polls.send(sample(at = 1_000))

        store.data.next { it != null } shouldBe sample(at = 1_000)
    }

    @Test
    fun drops_a_sample_older_than_three_intervals() = runTest {
        val polls = Channel<KioskStats?>()
        val store = KioskStatsStore(interval = 1.milliseconds, load = { polls.receive() }, clock = { Instant.fromEpochSeconds(1_015) }, job = job)

        coroutineScope {
            val published = async { store.data.drop(1).first() }
            polls.send(sample(at = 1_000))
            polls.send(sample(at = 1_001))

            withTimeout(1.seconds) { published.await() } shouldBe sample(at = 1_001)
        }
    }
}

private fun sample(at: Long): KioskStats = KioskStats(at = at, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)

private suspend fun <T> Flow<T>.next(predicate: (T) -> Boolean): T = withTimeout(1.seconds) { first(predicate) }
