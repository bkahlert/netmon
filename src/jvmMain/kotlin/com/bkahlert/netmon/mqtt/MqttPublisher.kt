package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.serialization.JsonFormat
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

/** Publisher based on the [Eclipse Paho MQTT 3 client](https://github.com/eclipse-paho/paho.mqtt.java), QoS 1, retained. */
class MqttPublisher<T>(
    val host: String,
    val port: Int,
    val path: String? = null,
    val stringFormat: StringFormat = JsonFormat,
    val serializer: SerializationStrategy<T>,
    identifier: String? = null,
) : Publisher<T> {
    private val logger by SLF4J

    val url: String = url(host, port, path)

    private val client: MqttClient = MqttClient(url, identifier ?: UUID.randomUUID().toString(), MemoryPersistence()).also {
        logger.info("Connecting to {}", url)
        it.connect(MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
        })
    }

    override fun publish(topic: String, event: T): Boolean {
        val payload = stringFormat.encodeToString(serializer, event).encodeToByteArray()
        logger.debug("Publishing message ({} bytes) to {}", payload.size, topic)
        return try {
            client.publish(topic, MqttMessage(payload).apply { qos = 1; isRetained = true })
            logger.info("Published message ({} bytes) to {}", payload.size, topic)
            true
        } catch (e: MqttException) {
            if (generateSequence<Throwable>(e) { it.cause }.any { it is InterruptedException }) {
                Thread.currentThread().interrupt()
                logger.debug("Interrupted publishing to {}", topic, e)
            } else {
                logger.error("Error publishing to {}", topic, e)
            }
            false
        }
    }

    override fun toString(): String = "${this::class.simpleName}(url=$url, connected=${client.isConnected})"

    companion object {
        /** Returns the broker URI: `ws://` for the broker's websocket ports 8080 and 8081, `tcp://` otherwise, [path] appended when given. */
        fun url(host: String, port: Int, path: String?): String =
            (if (port == 8080 || port == 8081) "ws" else "tcp") + "://$host:$port" + path?.let { "/$it" }.orEmpty()
    }
}
