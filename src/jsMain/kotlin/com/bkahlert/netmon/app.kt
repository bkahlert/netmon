package com.bkahlert.netmon

import com.bkahlert.kommons.js.OnScreenConsole
import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.time.Now
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.kommons.uri.queryParameters
import com.bkahlert.kommons.uri.toUri
import com.bkahlert.netmon.ui.scan
import dev.fritz2.core.handledBy
import dev.fritz2.core.mapByKey
import dev.fritz2.core.render
import kotlinx.browser.window
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import mqtt.MQTT
import mqtt.messages
import mqtt.onClose
import mqtt.onConnect
import mqtt.onDisconnect
import mqtt.onError
import mqtt.subscribe

@JsModule("./images/loading.svg")
@JsNonModule
private external val loadingImage: String

suspend fun main() {
    // Keep a reference to make sure it's part of the release
    loadingImage

    // When running on an embedded device, the console log is practically inaccessible.
    // Therefore, an on-screen console is used for the first log messages to be readable.
    val onScreenConsole = OnScreenConsole(console).apply {
        enable()
        console.info("On-screen console enabled")
    }


    /*
     * Status
     */
    val consoleLogStore = ConsoleLogStore("info" to "Starting...")
    render("#root.app .status") {
        h1("font-bold") { +"Network Monitor" }
        div("opacity-50") {
            val start = Now
            ticks(Settings.WebDisplay.REFRESH_INTERVAL).map {
                start.toMomentString()
            }.render(into = this) { +"started $it" }
        }
        div("flex-1 text-right truncate font-mono") {
            // Always show the last (relevant) log message at the top of the app.
            consoleLogStore.data.render(this) { (fn, message) ->
                span(
                    when (fn) {
                        "error" -> "text-red-500"
                        "warn" -> "text-yellow-500 opacity-75"
                        else -> "opacity-50"
                    }
                ) { +message }
            }
        }
    }


    /*
     * Network Scans
     */
    val scanEventsStore = ScanEventsStore()
    render("#root.app .networks") {
        div("sm:grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-4") {
            scanEventsStore.data.map { it.keys.toList() }.renderEach(
                idProvider = { it.toString() },
                into = this,
            ) { source ->
                scan(source, scanEventsStore.mapByKey(source).data)
            }
        }
    }


    /*
     * MQTT
     */
    val parameters = window.location.href.toUri().queryParameters
    val brokerHost = parameters["broker.host"] ?: Settings.BROKER_HOST
    val brokerPort = parameters["broker.port"] ?: Settings.WebDisplay.BROKER_PORT
    val brokerUrl = parameters["broker.url"] ?: "ws://$brokerHost:$brokerPort"
    val scanTopic = (parameters["topic.scan"] ?: Settings.SCAN_TOPIC)
        .replace("\${node}", "+")
        .replace("\${interface}", "+")
        .replace("\${cidr}", "+/+")

    MQTT.connect(brokerUrl).apply {
        console.info("MQTT::Connecting to [%s]...", brokerUrl)

        onConnect { packet ->
            console.info("MQTT::Connected", packet)
            subscribe(scanTopic) { qos = 1 }
            console.info("MQTT::Subscribed on %s to %s", brokerUrl, scanTopic)
            console.debug("Hiding on-screen console when as soon as first event was processed...")
        }

        messages
            .filter { (topic, _, _) -> topic.endsWith("/scan") }
            .transform { (topic, message, _) ->
                runCatching {
                    val source = EventSource.fromTopic(topic)
                    val event = JsonFormat.decodeFromString<Event.ScanEvent>(message.decodeToString())
                    source to event
                }.fold(
                    onSuccess = { emit(it) },
                    onFailure = { console.error("Failed to decode MQTT message", it) }
                )
            }
            .onEach { console.debug("MQTT::Event received", it) }
            .onEach { onScreenConsole.disable() } handledBy scanEventsStore.process

        onError { console.error("MQTT", it) }
        onDisconnect { console.warn("MQTT::Disconnection packet received from broker", it) }
        onClose { console.warn("MQTT::Disconnected") }
    }

}
