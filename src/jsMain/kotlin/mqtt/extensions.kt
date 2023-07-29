package mqtt

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/* Callback-based extensions */

/**
 * Subscribes to the specified [topic] with the given [options].
 * @see <a href="https://github.com/mqttjs/MQTT.js/#mqttclientsubscribetopictopic-arraytopic-object-options-callback">Event 'connect'</a>
 */
fun MqttClient.subscribe(topic: String, options: ClientSubscribeOptions.() -> Unit): MqttClient =
    subscribe(topic, js("{}").unsafeCast<ClientSubscribeOptions>().apply(options))

/**
 * Registers the given [callback] which is invoked on successful (re)connection (i.e., connack rc=0).
 * @see <a href="https://github.com/mqttjs/MQTT.js/#event-connect">Event 'connect'</a>
 */
fun MqttClient.onConnect(callback: (packet: dynamic) -> Unit): Unit = on("connect", callback)

/**
 * Registers the given [callback] which is invoked after a disconnection.
 * @see <a href="https://github.com/mqttjs/MQTT.js/#event-close">Event 'close'</a>
 */
fun MqttClient.onClose(callback: () -> Unit): Unit = on("close", callback)

/**
 * Registers the given [callback] which is invoked after receiving disconnect packet from broker. MQTT 5.0 feature.
 * @see <a href="https://github.com/mqttjs/MQTT.js/#event-disconnect">Event 'disconnect'</a>
 */
fun MqttClient.onDisconnect(callback: (packet: dynamic) -> Unit): Unit = on("disconnect", callback)

/**
 * Registers the given [callback] which is invoked when the client can't connect (i.e., connack rc != 0) or when a parsing error occurs.
 * @see <a href="https://github.com/mqttjs/MQTT.js/#event-error">Event 'error'</a>
 */
fun MqttClient.onError(callback: (error: dynamic) -> Unit): Unit = on("error", callback)

/**
 * Registers the given [callback] which is invoked when the client receives a publish packet.
 * @see <a href="https://github.com/mqttjs/MQTT.js/#event-message">Event 'message'</a>
 */
fun MqttClient.onMessage(callback: (topic: String, message: ByteArray, packet: dynamic) -> Unit): Unit = on("message", callback)

/* Flow-based extensions */

/**
 * Returns a callback flow that
 * —for as long as its consumed—
 * emits the parameters of each occurred [event].
 */
fun MqttClient.on(event: String): Flow<Array<dynamic>> = callbackFlow {
    val listener: dynamic = {
        trySend(js("Array.from(arguments)").unsafeCast<Array<dynamic>>())
    }

    this@on.on(event, listener)
    awaitClose { this@on.off(event, listener) }
}

/**
 * An MQTT message as emitted by [MqttClient.messages].
 */
typealias MqttMessage = Triple<String, ByteArray, dynamic>

/**
 * Flow of [MqttMessage] instances.
 */
val MqttClient.messages: Flow<MqttMessage>
    get() = on("message").map { (topic, message, packet) ->
        MqttMessage(topic.unsafeCast<String>(), message.unsafeCast<ByteArray>(), packet)
    }
