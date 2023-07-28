package mqtt

fun MqttClient.subscribe(topic: String, opts: ClientSubscribeOptions.() -> Unit): MqttClient =
    subscribe(topic, js("{}").unsafeCast<ClientSubscribeOptions>().apply(opts))

fun MqttClient.onConnect(callback: () -> Unit) = on("connect", callback)
fun MqttClient.onMessage(callback: (topic: String, message: ByteArray, packet: dynamic) -> Unit) = on("message", callback)
fun MqttClient.onError(callback: (error: dynamic) -> Unit) = on("error", callback)
fun MqttClient.onDisconnect(callback: (packet: dynamic) -> Unit) = on("disconnect", callback)
fun MqttClient.onClose(callback: () -> Unit) = on("close", callback)
