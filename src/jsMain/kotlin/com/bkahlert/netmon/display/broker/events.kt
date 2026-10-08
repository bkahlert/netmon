package com.bkahlert.netmon.display.broker

import com.bkahlert.netmon.display.app.BrokerSettings
import com.bkahlert.netmon.contract.Event
import com.bkahlert.netmon.contract.EventSource
import com.bkahlert.netmon.contract.ScanTopics
import com.bkahlert.netmon.contract.serialization.JsonFormat
import com.bkahlert.netmon.display.app.DisplayScanSettings
import com.bkahlert.netmon.display.metrics.METRICS_TOPIC
import com.bkahlert.netmon.display.support.console.console
import com.bkahlert.netmon.support.text.Template
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import mqtt.MQTT
import mqtt.MqttMessage
import mqtt.messages
import mqtt.onClose
import mqtt.onConnect
import mqtt.onDisconnect
import mqtt.onError
import mqtt.subscribe
import mqtt.url
import com.bkahlert.netmon.display.networks.host

/** Returns the messages of the [topics], subscribed with QoS 1 on one client of the broker at [brokerHost] and [brokerPort] while the flow is collected. */
fun mqttMessageFlow(
    topics: List<String>,
    brokerHost: String = BrokerSettings.host,
    brokerPort: Int = BrokerSettings.port,
): Flow<MqttMessage> = callbackFlow {

    val client = MQTT.connect("ws://$brokerHost:$brokerPort").apply {
        console.info("MQTT::Connecting to [%s]...", options.url)
        onConnect { console.info("MQTT::Connected") }
        onError { console.error("MQTT", it) }
        onDisconnect { console.warn("MQTT::Disconnection packet received from broker", it) }
        onClose { console.warn("MQTT::Disconnected") }
    }

    client.subscribe(topics) { qos = 1 }
    client.messages
        .onEach { message -> trySend(message).onFailure { console.error("Failed to send event", it) } }
        .stateIn(this@callbackFlow)

    awaitClose { client.end() }
}

private val SCAN_TOPIC = ScanTopics.subscription(DisplayScanSettings.topic)

/** Returns the messages of the scan and metrics topics, over one connection [connect] opens once [scope] has a consumer. */
fun brokerMessages(scope: CoroutineScope, connect: (List<String>) -> Flow<MqttMessage> = { mqttMessageFlow(it) }): Flow<MqttMessage> =
    connect(listOf(SCAN_TOPIC, METRICS_TOPIC)).shareIn(scope, SharingStarted.Lazily, replay = 1)

/** Returns the scans among these messages, decoded; a scan that does not decode is logged and left out. */
fun Flow<MqttMessage>.scans(template: Template = DisplayScanSettings.topic): Flow<Pair<EventSource, Event.ScanEvent>> {
    val pattern = ScanTopics.pattern(template)
    return filter { (topic, _, _) -> pattern.matches(topic) }
        .mapNotNull { (topic, message, _) ->
            runCatching { EventSource.fromTopic(topic, template) to JsonFormat.decodeFromString<Event.ScanEvent>(message.decodeToString()) }
                .onFailure { console.error("Failed to decode MQTT message", it) }
                .getOrNull()
        }
}

private val METRICS_PATTERN = Regex("dt/netmon/[^/]+/metrics")

/** Returns the payloads of the metrics topic among these messages. */
fun Flow<MqttMessage>.metricsPayloads(): Flow<ByteArray> =
    filter { (topic, _, _) -> METRICS_PATTERN.matches(topic) }.map { (_, payload, _) -> payload }
