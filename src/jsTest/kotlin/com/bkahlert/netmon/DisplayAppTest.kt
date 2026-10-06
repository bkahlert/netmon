package com.bkahlert.netmon

import com.bkahlert.netmon.Event.ScanEvent
import com.bkahlert.netmon.fritz2.runTest
import com.bkahlert.netmon.serialization.JsonFormat
import com.bkahlert.netmon.ui.awaited
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import mqtt.MqttMessage
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DisplayAppTest {

    @Test
    fun shares_one_broker_connection_and_clears_only_its_targets_on_disposal() = runTest {
        var starts = 0
        var active = 0
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val messages: Flow<MqttMessage> = flow {
            starts++
            active++
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                active--
                stopped.complete(Unit)
            }
        }
        val statusTarget = target()
        val networksTarget = target()
        val unrelatedTarget = target().apply { textContent = "untouched" }

        val display = app(statusTarget, networksTarget, messages = messages)
        withTimeout(1.seconds) { started.await() }
        active shouldBe 1
        starts shouldBe 1

        display.dispose()
        withTimeout(1.seconds) { stopped.await() }

        statusTarget.childNodes.length shouldBe 0
        networksTarget.childNodes.length shouldBe 0
        unrelatedTarget.textContent shouldBe "untouched"
        active shouldBe 0
        statusTarget.remove()
        networksTarget.remove()
        unrelatedTarget.remove()
    }

    @Test
    fun a_second_instance_has_no_subscriptions_from_a_disposed_instance() = runTest {
        var starts = 0
        var active = 0
        val firstStarted = CompletableDeferred<Unit>()
        val firstStopped = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val secondStopped = CompletableDeferred<Unit>()
        val messages: Flow<MqttMessage> = flow {
            val connection = ++starts
            active++
            when (connection) {
                1 -> firstStarted.complete(Unit)
                2 -> secondStarted.complete(Unit)
            }
            try {
                awaitCancellation()
            } finally {
                active--
                when (connection) {
                    1 -> firstStopped.complete(Unit)
                    2 -> secondStopped.complete(Unit)
                }
            }
        }
        val firstStatus = target()
        val firstNetworks = target()
        val secondStatus = target()
        val secondNetworks = target()

        val first = app(firstStatus, firstNetworks, messages = messages)
        withTimeout(1.seconds) { firstStarted.await() }
        first.dispose()
        withTimeout(1.seconds) { firstStopped.await() }
        active shouldBe 0

        val second = app(secondStatus, secondNetworks, messages = messages)
        withTimeout(1.seconds) { secondStarted.await() }
        starts shouldBe 2
        active shouldBe 1

        second.dispose()
        withTimeout(1.seconds) { secondStopped.await() }
        active shouldBe 0
        listOf(firstStatus, firstNetworks, secondStatus, secondNetworks).forEach(HTMLElement::remove)
    }

    @Test
    fun calls_on_success_only_after_the_first_scan_arrives() = runTest {
        val incoming = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 1)
        val statusTarget = target()
        val networksTarget = target()
        val success = CompletableDeferred<Unit>()
        var successCalls = 0

        val display = app(statusTarget, networksTarget, messages = incoming) {
            successCalls++
            success.complete(Unit)
        }
        withTimeout(1.seconds) { incoming.subscriptionCount.first { it > 0 } }
        yield()
        successCalls shouldBe 0

        incoming.emit(scanMessage(Clock.System.now()))
        withTimeout(1.seconds) { success.await() }
        successCalls shouldBe 1

        display.dispose()
        statusTarget.remove()
        networksTarget.remove()
    }

    @Test
    fun delivers_a_scan_emitted_as_the_broker_starts() = runTest {
        val connected = CompletableDeferred<Unit>()
        val received = CompletableDeferred<Unit>()
        val timestamp = Clock.System.now()
        val messages: Flow<MqttMessage> = flow {
            connected.complete(Unit)
            emit(scanMessage(timestamp))
            awaitCancellation()
        }
        val statusTarget = target()
        val networksTarget = target()
        val display = app(statusTarget, networksTarget, messages = messages) { received.complete(Unit) }

        try {
            withTimeout(1.seconds) { connected.await() }
            withTimeout(1.seconds) { received.await() }
        } finally {
            display.dispose()
            statusTarget.remove()
            networksTarget.remove()
        }
    }

    @Test
    fun evaluates_kiosk_freshness_with_the_app_clock() = runTest {
        val clock = FixedClock(Instant.fromEpochSeconds(1_006))
        val incoming = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 1)
        val statusTarget = target()
        val networksTarget = target()
        val display = app(statusTarget, networksTarget, messages = incoming, clock = clock)

        try {
            withTimeout(1.seconds) { incoming.subscriptionCount.first { it > 0 } }
            incoming.emit(metricsMessage(at = 1_000))

            val text = statusTarget.awaited({ textContent.orEmpty() }) { "kiosk 161 MB" in it }
            text shouldContain "kiosk 161 MB"
        } finally {
            display.dispose()
            statusTarget.remove()
            networksTarget.remove()
        }
    }

    @Test
    fun cancellation_during_mount_leaves_no_late_content_or_broker_collector() = runTest {
        var starts = 0
        val messages: Flow<MqttMessage> = flow {
            starts++
            awaitCancellation()
        }
        val statusTarget = target()
        val networksTarget = target()

        coroutineScope {
            val mounting = async(start = CoroutineStart.UNDISPATCHED) {
                app(statusTarget, networksTarget, messages = messages)
            }
            mounting.cancelAndJoin()
            yield()
        }

        statusTarget.childNodes.length shouldBe 0
        networksTarget.childNodes.length shouldBe 0
        starts shouldBe 0
        statusTarget.remove()
        networksTarget.remove()
    }
}

private fun target(): HTMLElement =
    (document.createElement("div") as HTMLElement).also { document.body?.appendChild(it) }

private fun scanMessage(timestamp: Instant): MqttMessage =
    MqttMessage(
        "dt/netmon/node/en0/198.51.100.0/24/scan",
        JsonFormat.encodeToString<Event>(ScanEvent(ScanEvent.Type.COMPLETED, emptyList(), timestamp)).encodeToByteArray(),
        null,
    )

private fun metricsMessage(at: Long): MqttMessage =
    MqttMessage(
        "dt/netmon/netmon/metrics",
        """{"resourceMetrics":[{"resource":{"attributes":[{"key":"systemd.unit.name","value":{"stringValue":"pihero-kiosk.service"}}]},
"scopeMetrics":[{"metrics":[{"name":"systemd.unit.memory.usage","sum":{"aggregationTemporality":2,"dataPoints":[
{"attributes":[{"key":"type","value":{"stringValue":"ram"}}],"timeUnixNano":"${at}000000000","asInt":"160000000"},
{"attributes":[{"key":"type","value":{"stringValue":"swap"}}],"timeUnixNano":"${at}000000000","asInt":"8820736"}]}}]}]}]}""".encodeToByteArray(),
        null,
    )

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
