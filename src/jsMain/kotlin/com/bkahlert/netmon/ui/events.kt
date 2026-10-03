package com.bkahlert.netmon.ui

import com.bkahlert.netmon.BrokerSettings
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.serialization.JsonFormat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
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

fun mqttMessageFlow(
    topic: String,
    brokerHost: String = BrokerSettings.host,
    brokerPort: Int = BrokerSettings.port,
): Flow<MqttMessage> = callbackFlow {

    val client = MQTT.connect("ws://$brokerHost:$brokerPort").apply {
        com.bkahlert.kommons.js.console.info("MQTT::Connecting to [%s]...", options.url)
        onConnect { com.bkahlert.kommons.js.console.info("MQTT::Connected") }
        onError { com.bkahlert.kommons.js.console.error("MQTT", it) }
        onDisconnect { com.bkahlert.kommons.js.console.warn("MQTT::Disconnection packet received from broker", it) }
        onClose { com.bkahlert.kommons.js.console.warn("MQTT::Disconnected") }
    }

    client.subscribe(topic) { qos = 1 }
    client.messages
        .onEach { message -> trySend(message).onFailure { com.bkahlert.kommons.js.console.error("Failed to send event", it) } }
        .stateIn(this@callbackFlow)

    awaitClose { client.end() }
}

fun scanFlow(
    topic: String = ScanEventSettings.topic.toString("node" to "+", "interface" to "+", "cidr" to "+/+"),
): Flow<Pair<EventSource, Event.ScanEvent>> =
    mqttMessageFlow(topic = topic)
        .mapNotNull { (topic, message, _) ->
            runCatching { EventSource.fromTopic(topic) to JsonFormat.decodeFromString<Event.ScanEvent>(message.decodeToString()) }
                .onFailure { com.bkahlert.kommons.js.console.error("Failed to decode MQTT message", it) }
                .getOrNull()
        }
