package mqtt

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
