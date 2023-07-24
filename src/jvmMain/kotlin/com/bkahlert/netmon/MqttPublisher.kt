package com.bkahlert.netmon

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.orNull
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
    val identifier: String? = null,
) : Publisher<T> {

    private val logger by SLF4J

    private val client = MqttClient.builder()
        .identifier(identifier ?: UUID.randomUUID().toString())
        .serverHost(host)
        .serverPort(port ?: 1883)
        .useMqttVersion5()
        .automaticReconnectWithDefaultConfig()
        .buildBlocking()
        .apply { connect() }

    override fun publish(topic: String, event: T): Boolean {
        val message = stringFormat.encodeToString(serializer, event)
        val bytes = message.encodeToByteArray()
        logger.debug("Publishing message ({} bytes) to {}", bytes.size, topic)

        val result = client
            .publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payloadFormatIndicator(Mqtt5PayloadFormatIndicator.UTF_8)
            .apply { if (stringFormat is Json) contentType("application/json") }
            .payload(bytes)
            .send()

        return when (val error = result.error.orNull()) {
            null -> {
                logger.debug("Published to {}: {}", topic, result)
                true
            }

            else -> {
                logger.error("Error publishing to {}", topic, error)
                false
            }
        }
    }
}
