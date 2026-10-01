package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.logging.SLF4J
import net.logstash.logback.argument.StructuredArguments.v
import com.bkahlert.kommons.orNull
import com.bkahlert.netmon.mqtt.GenericMqttClient.Companion.generic
import com.bkahlert.netmon.serialization.JsonFormat
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientBuilder
import com.hivemq.client.mqtt.MqttWebSocketConfig
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.datatypes.MqttTopic
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PayloadFormatIndicator
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import java.util.UUID

/** Publisher based on the [HiveMQ MQTT Client](https://github.com/hivemq/hivemq-mqtt-client). */
class MqttPublisher<T>(
    val host: String,
    val port: Int,
    val path: String? = null,
    val stringFormat: StringFormat = JsonFormat,
    val serializer: SerializationStrategy<T>,
    identifier: String? = null,
) : Publisher<T> {
    private val logger by SLF4J

    private val url = "$host:$port${path?.let { "/$it" }.orEmpty()}"

    private val client: GenericMqttClient<*, *> = MqttClient.builder()
        .identifier(identifier ?: UUID.randomUUID().toString())
        .serverHost(host)
        .serverPort(port)
        .apply {
            if (port == 8080 || port == 8081) webSocketConfig(MqttWebSocketConfig.builder().serverPath(path ?: "").build())
        }
        .generic()

    private val ack = client.run {
        logger.info("Connecting to {}:{}{}", v("host", host), v("port", port), v("path", path?.let { "/$it" }.orEmpty()))
        connect()
    }

    override fun publish(topic: String, event: T): Boolean {
        val message = stringFormat.encodeToString(serializer, event)
        val payload = message.encodeToByteArray()
        logger.debug("Publishing message ({} bytes) to {}", payload.size, topic)

        val result = client.publish(
            topic = MqttTopic.of(topic),
            qos = MqttQos.AT_LEAST_ONCE,
            retain = true,
            payload = payload,
            payloadFormatIndicator = Mqtt5PayloadFormatIndicator.UTF_8,
            contentType = if (stringFormat is Json) "application/json" else null,
        )

        return when (val error = (result as? Mqtt5PublishResult)?.error.orNull()) {
            null -> {
                when (result) {
                    Unit -> logger.info("Published message ({} bytes) to {}", payload.size, topic)
                    else -> logger.info("Published message ({} bytes) to {}: {}", payload.size, topic, result)
                }
                true
            }

            else -> {
                logger.error("Error publishing to {}", topic, error)
                false
            }
        }
    }

    override fun toString(): String = "${this::class.simpleName}(url=$url, status=$ack)"
}

/** Generic MQTT client in the attempt to support both MQTT 3 and MQTT 5 interchangeably. */
@Suppress("LongLine")
sealed interface GenericMqttClient<ACK, PUBLISH_RESULT> {
    /** Connects this client with the default Connect message. */
    fun connect(): ACK

    // @formatter:off
    /** Builds the MQTT publish and applies it to the parent which then sends the message. */
    fun publish(topic: MqttTopic, qos: MqttQos, retain: Boolean, payload: ByteArray, payloadFormatIndicator: Mqtt5PayloadFormatIndicator?, contentType: String?): PUBLISH_RESULT

    /** [Mqtt3BlockingClient] based implementation of the [GenericMqttClient] */
    class Mqtt3Client(clientBuilder: MqttClientBuilder) : GenericMqttClient<Mqtt3ConnAck, Unit> {
        private val client:Mqtt3BlockingClient = clientBuilder.useMqttVersion3().automaticReconnectWithDefaultConfig().buildBlocking()
        override fun connect(): Mqtt3ConnAck = client.connect()
        override fun publish(topic: MqttTopic, qos: MqttQos, retain: Boolean, payload: ByteArray, payloadFormatIndicator: Mqtt5PayloadFormatIndicator?, contentType: String?) {
            val message = Mqtt3Publish.builder().topic(topic).qos(qos).retain(retain).payload(payload)
                .build()
            return client.publish(message)
        }
    }

    /** [Mqtt5BlockingClient] based implementation of the [GenericMqttClient] */
    class Mqtt5Client(clientBuilder: MqttClientBuilder) : GenericMqttClient<Mqtt5ConnAck, Mqtt5PublishResult> {
        private val client:Mqtt5BlockingClient = clientBuilder.useMqttVersion5().automaticReconnectWithDefaultConfig().buildBlocking()
        override fun connect(): Mqtt5ConnAck = client.connect()
        override fun publish(topic: MqttTopic, qos: MqttQos, retain: Boolean, payload: ByteArray, payloadFormatIndicator: Mqtt5PayloadFormatIndicator?, contentType: String?): Mqtt5PublishResult {
            val message = Mqtt5Publish.builder().topic(topic).qos(qos).retain(retain).payload(payload)
                .payloadFormatIndicator(payloadFormatIndicator).contentType(contentType)
                .build()
            return client.publish(message)
        }
    }
    // @formatter:on

    companion object {
        /** Creates a [GenericMqttClient] based on the given [MqttClientBuilder]. */
        fun MqttClientBuilder.generic(): GenericMqttClient<*, *> = Mqtt3Client(this)
    }
}
