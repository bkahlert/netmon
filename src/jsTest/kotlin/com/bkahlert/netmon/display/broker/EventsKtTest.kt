package com.bkahlert.netmon.display.broker

import com.bkahlert.netmon.display.metrics.METRICS_TOPIC
import com.bkahlert.netmon.display.support.fritz2.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import mqtt.MqttMessage
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class EventsKtTest {

    @Test
    fun keeps_only_the_payloads_of_the_metrics_topic() = runTest {
        val messages = flowOf(message("dt/netmon/netmon/eth0/198.51.100.0/24/scan", "{}"), message("dt/netmon/netmon/metrics", "m"))

        val payloads = messages.metricsPayloads().toList()

        payloads.map { it.decodeToString() } shouldBe listOf("m")
    }

    @Test
    fun keeps_the_metrics_out_of_the_scans() = runTest {
        val scans = flowOf(message("dt/netmon/netmon/metrics", "{}")).scans().toList()

        scans shouldBe emptyList()
    }

    @Test
    fun connects_once_for_both_topics_and_hands_the_first_message_to_every_consumer() = runTest {
        val connects = mutableListOf<List<String>>()
        val messages = brokerMessages(CoroutineScope(job)) { topics ->
            connects += topics
            flow { emit(message("dt/netmon/netmon/metrics", "m")) }
        }

        coroutineScope {
            val first = async { messages.first() }
            val second = async { messages.first() }

            withTimeout(1.seconds) { first.await().first } shouldBe "dt/netmon/netmon/metrics"
            withTimeout(1.seconds) { second.await().first } shouldBe "dt/netmon/netmon/metrics"
            connects shouldBe listOf(listOf("dt/netmon/+/+/+/+/scan", METRICS_TOPIC))
        }
    }

    @Test
    fun late_subscriber_receives_the_first_broker_message() = runTest {
        val emitted = CompletableDeferred<Unit>()
        val messages = brokerMessages(CoroutineScope(job)) {
            flow {
                emit(message("dt/netmon/netmon/metrics", "m"))
                emitted.complete(Unit)
                awaitCancellation()
            }
        }

        val first = withTimeout(1.seconds) { messages.first() }
        first.second.decodeToString() shouldBe "m"
        emitted.isCompleted shouldBe true

        val second = withTimeout(1.seconds) { messages.first() }
        second.second.decodeToString() shouldBe "m"
    }
}

private fun message(topic: String, payload: String): MqttMessage = MqttMessage(topic, payload.encodeToByteArray(), null)
