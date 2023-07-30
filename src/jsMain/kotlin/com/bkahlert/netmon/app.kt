package com.bkahlert.netmon

import com.bkahlert.kommons.js.OnScreenConsole
import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.kommons.uri.fragmentParameters
import com.bkahlert.kommons.uri.queryParameters
import com.bkahlert.kommons.uri.toUri
import com.bkahlert.netmon.net.HostEventsStore
import com.bkahlert.netmon.net.ScanEventsStore
import com.bkahlert.netmon.net.decode
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import com.bkahlert.netmon.ui.host
import com.bkahlert.netmon.ui.icon
import com.bkahlert.netmon.ui.networks
import com.bkahlert.netmon.ui.panel
import dev.fritz2.core.handledBy
import dev.fritz2.core.render
import io.ktor.http.ParametersBuilder
import kotlinx.browser.window
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import mqtt.MQTT
import mqtt.messages
import mqtt.onClose
import mqtt.onConnect
import mqtt.onDisconnect
import mqtt.onError
import mqtt.subscribe

@JsModule("./loading.svg")
@JsNonModule
private external val loadingImage: String

suspend fun main() {
    loadingImage

    val onScreenConsole = OnScreenConsole(console).apply { enable() }

    val parameters = window.location.href.toUri().run {
        ParametersBuilder().apply {
            appendAll(queryParameters.also { console.debug("Query parameters %s", it) })
            appendAll(fragmentParameters.also { console.debug("Fragment parameters %s", it) })
        }
    }

    val brokerHost = parameters["broker.host"] ?: Settings.brokerHost
    val brokerPort = parameters["broker.port"] ?: Settings.WebDisplay.brokerPort
    val brokerUrl = parameters["broker.url"] ?: "ws://$brokerHost:$brokerPort"

    val hostEventsStore = HostEventsStore()
    val scanEventsStore = ScanEventsStore()

    MQTT.connect(brokerUrl).apply {
        console.info("MQTT", "Connecting...")

        onConnect { packet ->
            console.info("MQTT", "Connected", packet)
            subscribe("dt/netmon/home/scan") { qos = 1 }
            subscribe("dt/netmon/home/host") { qos = 1 }
            console.info("MQTT", "Subscription initiated")
            console.info("Hiding on-screen console when as soon as first event was processed...")
        }

        messages
            .filter { (topic, _, _) -> topic == "dt/netmon/home/scan" }
            .decode<Event.ScanEvent>()
            .onEach { onScreenConsole.disable() } handledBy scanEventsStore.process

        messages
            .filter { (topic, _, _) -> topic == "dt/netmon/home/host" }
            .decode<Event.HostEvent>()
            .onEach { onScreenConsole.disable() } handledBy hostEventsStore.process

        onError { console.error("MQTT", it) }
        onDisconnect { console.warn("MQTT", "Disconnection packet received from broker", it) }
        onClose { console.warn("MQTT", "Disconnected") }
    }

    render("#root") {
        networks(scanEventsStore) { (_, network, hosts, timestamp) ->
            panel(
                name = network.hostname,
                icon = SolidHeroIcons.server,
                extra = {
                    ul("flex flex-col items-end text-xs") {
                        li {
                            span("font-semibold") { +network.cidr.toString() }
                            +" on "
                            span("font-semibold") { +network.`interface` }
                        }
                        li {
                            span("font-semibold") { ticks().render(into = this) { +timestamp.toMomentString() } }
                        }
                    }
                },
            ) {
                hostEventsStore.data.map { it.filter { it.network == network } }.renderEach(idProvider = { it.host }) { event ->
                    when (event.type) {
                        Event.HostEvent.Type.UP -> div("bg-gradient-to-r from-green-700 to-transparent shadow-xl") {
                            icon("shrink-0 w-12 h-12 text-green-600", SolidHeroIcons.arrow_up_circle)
                            +"${event.host.ip} is up"
                        }

                        Event.HostEvent.Type.DOWN -> div("bg-gradient-to-r from-red-700 to-transparent shadow-xl") {
                            icon("shrink-0 w-12 h-12 text-red-600", SolidHeroIcons.arrow_down_circle)
                            +"${event.host.ip} is down"
                        }
                    }
                }

                ul("grid grid-cols-[repeat(auto-fit,minmax(min-content,200px))] gap-4") {
                    hosts.forEach { host ->
                        li { host(host) }
                    }
                }
            }
        }
    }
}
