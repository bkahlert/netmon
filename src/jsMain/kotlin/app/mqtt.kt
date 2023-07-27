package app

@JsModule("./mqtt")
@JsNonModule
external object MQTT {
    fun connect(brokerUrl: String): MqttClient
}

external object MqttClient {
    fun subscribe(topic: String, opts: ClientSubscribeOptions = definedExternally): MqttClient
    fun <T : Function<Unit>> on(event: String, callback: T)
}

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

fun MqttClient.onConnect(callback: () -> Unit) {
    on("connect", callback)
}

fun MqttClient.subscribe(topic: String, opts: ClientSubscribeOptions.() -> Unit): MqttClient =
    subscribe(topic, js("{}").unsafeCast<ClientSubscribeOptions>().apply(opts))

fun MqttClient.onMessage(callback: (topic: String, message: String, packet: dynamic) -> Unit) {
    on("message") { topic: String, message: ByteArray, packet: dynamic ->
        callback(topic, message.decodeToString(), packet)
    }
}

fun MqttClient.onError(callback: (error: dynamic) -> Unit) {
    on("error", callback)
}

fun MqttClient.onDisconnect(callback: (packet: dynamic) -> Unit) {
    on("disconnect", callback)
}

fun MqttClient.onClose(callback: () -> Unit) {
    on("close", callback)
}
