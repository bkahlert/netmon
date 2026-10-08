package com.bkahlert.netmon.display.metrics

import com.bkahlert.netmon.display.support.fritz2.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class KioskStatsStoreTest {

    @Test
    fun holds_the_latest_sample_and_drops_it_on_an_empty_payload() = runTest {
        val payloads = Channel<ByteArray>()
        val store = KioskStatsStore(payloads.consumeAsFlow(), interval = 1.milliseconds, clock = { Instant.fromEpochSeconds(1_006) }, job = job)

        payloads.send(payload(at = 1_000))
        store.data.next { it != null } shouldBe sample(at = 1_000)
        payloads.send(payload(at = 1_005))
        store.data.next { it != sample(at = 1_000) } shouldBe sample(at = 1_005)
        payloads.send(ByteArray(0))

        store.data.next { it == null } shouldBe null
    }

    @Test
    fun takes_the_next_sample_after_one_that_does_not_decode() = runTest {
        val payloads = Channel<ByteArray>()
        val store = KioskStatsStore(payloads.consumeAsFlow(), interval = 1.milliseconds, clock = { Instant.fromEpochSeconds(1_006) }, job = job)

        payloads.send("{".encodeToByteArray())
        payloads.send(payload(at = 1_000))

        store.data.next { it != null } shouldBe sample(at = 1_000)
    }

    @Test
    fun drops_a_sample_older_than_three_intervals() = runTest {
        val payloads = Channel<ByteArray>()
        val store = KioskStatsStore(payloads.consumeAsFlow(), interval = 1.milliseconds, clock = { Instant.fromEpochSeconds(1_015) }, job = job)

        coroutineScope {
            val published = async { store.data.drop(1).first() }
            payloads.send(payload(at = 1_000))
            payloads.send(payload(at = 1_001))

            withTimeout(1.seconds) { published.await() } shouldBe sample(at = 1_001)
        }
    }

    @Test
    fun drops_the_sample_once_it_ages_past_three_intervals_without_a_new_message() = runTest {
        val payloads = Channel<ByteArray>()
        var now = Instant.fromEpochSeconds(1_006)
        val store = KioskStatsStore(payloads.consumeAsFlow(), interval = 1.milliseconds, clock = { now }, job = job)
        payloads.send(payload(at = 1_000))
        store.data.next { it != null }

        now = Instant.fromEpochSeconds(1_015)

        store.data.next { it == null } shouldBe null
    }
}

private fun sample(at: Long): KioskStats = KioskStats(at = at, interval = 5, kioskMemory = 168_820_736)

private fun payload(at: Long): ByteArray = """{"resourceMetrics":[{"resource":{"attributes":[{"key":"systemd.unit.name","value":{"stringValue":"pihero-kiosk.service"}}]},
"scopeMetrics":[{"metrics":[{"name":"systemd.unit.memory.usage","sum":{"aggregationTemporality":2,"dataPoints":[
{"attributes":[{"key":"type","value":{"stringValue":"ram"}}],"timeUnixNano":"${at}000000000","asInt":"160000000"},
{"attributes":[{"key":"type","value":{"stringValue":"swap"}}],"timeUnixNano":"${at}000000000","asInt":"8820736"}]}}]}]}]}""".encodeToByteArray()

private suspend fun <T> Flow<T>.next(predicate: (T) -> Boolean): T = withTimeout(1.seconds) { first(predicate) }
