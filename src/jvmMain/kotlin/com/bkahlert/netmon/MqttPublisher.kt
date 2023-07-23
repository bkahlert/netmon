package com.bkahlert.netmon

import com.bkahlert.io.Logger
import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.serialization.JsonFormat
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import java.util.UUID

data class MqttPublisher<T>(
    val host: String,
    val port: Int? = null,
    val stringFormat: StringFormat = JsonFormat,
    val serializer: SerializationStrategy<T>,
    val identifier: String? = CommandLine("hostname").exec().readTextOrThrow().lowercase().substringBefore("."),
) : Publisher<T> {

    private val client = MqttClient.builder()
        .identifier(identifier ?: UUID.randomUUID().toString())
        .serverHost(host)
        .serverPort(port ?: 1883)
        .useMqttVersion5()
        .automaticReconnectWithDefaultConfig()
        .buildAsync()

    private val connection by lazy {
        client.connect()
    }

    override fun publish(topic: String, event: T) {
        val message = stringFormat.encodeToString(serializer, event)
        val bytes = message.encodeToByteArray()
        Logger.debug("Publishing message (${bytes.size} bytes) to $topic")

        connection
            .thenCompose {
                client.publishWith()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
                    .apply { if (stringFormat is Json) contentType("application/json") }
                    .payload(bytes)
                    .send()
            }
            .handle { _, error ->
                if (error != null) Logger.error("Error publishing to $topic: $error")
            }
    }
}
