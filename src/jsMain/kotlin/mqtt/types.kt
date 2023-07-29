package mqtt

/**
 * Entrypoint for the [MQTT client](https://github.com/mqttjs/MQTT.js/#api)
 */
@JsModule("./mqtt")
@JsNonModule
external object MQTT {
    /**
     * Connects to the broker specified by the given [url] and [options],
     * and returns a [MqttClient].
     *
     * @see <a href="https://github.com/mqttjs/MQTT.js/#mqttconnecturl-options">mqtt.connect([url], options)</a>
     */
    fun connect(url: String, options: dynamic = definedExternally): MqttClient
}

/**
 * [MQTT client](https://github.com/mqttjs/MQTT.js/#api)
 */
external object MqttClient {
    /**
     * Subscribes to the specified [topic] with the given [options].
     * @see <a href="https://github.com/mqttjs/MQTT.js/#mqttclientsubscribetopictopic-arraytopic-object-options-callback">Event 'connect'</a>
     */
    fun subscribe(topic: String, options: ClientSubscribeOptions = definedExternally): MqttClient

    /**
     * Registers the given [callback] which is invoked when the specified [event].
     *
     * @see <a href="https://github.com/mqttjs/MQTT.js/#event-connect">Event overview</a>
     */
    fun <T : Function<Unit>> on(event: String, callback: T)

    /**
     * Unregisters the previously registered [callback] from invocations for the specified [event].
     */
    fun <T : Function<Unit>> off(event: String, callback: T)
}

/**
 * Options for [MqttClient.subscribe]
 */
external interface ClientSubscribeOptions {
    /**
     * 0: Received at most once
     * The packet is sent, and that's it.
     * There is no validation about whether it has been received.
     *
     * 1: Received at least once
     * The packet is sent and stored as long as the client hasn't
     * received a confirmation from the server.
     * MQTT ensures that it's going to be received, but there can be duplicates.
     *
     * 2: Received exactly once.
     * Same as QoS 1, but there are no duplicates.
     */
    var qos: Int
}
