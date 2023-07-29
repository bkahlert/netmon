package com.bkahlert.netmon

import com.bkahlert.kommons.js.attachTo
import com.bkahlert.kommons.js.catchErrors
import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.kommons.uri.fragmentParameters
import com.bkahlert.kommons.uri.queryParameters
import com.bkahlert.kommons.uri.toUri
import com.bkahlert.netmon.net.HostEventsStore
import com.bkahlert.netmon.net.ScanEventsStore
import com.bkahlert.netmon.net.decode
import com.bkahlert.netmon.ui.HostNode
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import com.bkahlert.netmon.ui.icon
import com.bkahlert.netmon.ui.panel
import com.bkahlert.netmon.ui.pills
import com.bkahlert.netmon.ui.subPanel
import com.bkahlert.netmon.ui.tree
import dev.fritz2.core.handledBy
import dev.fritz2.core.render
import io.ktor.http.ParametersBuilder
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.dom.appendElement
import mqtt.MQTT
import mqtt.messages
import mqtt.onClose
import mqtt.onConnect
import mqtt.onDisconnect
import mqtt.onError
import mqtt.subscribe
import kotlin.time.Duration.Companion.seconds

@JsModule("./loading.svg")
@JsNonModule
private external val loadingImage: String

fun main() {
    loadingImage

    if (Settings.WebDisplay.globalCatch) {
        window.catchErrors()
    }
    if (Settings.WebDisplay.onScreenConsole) {
        window.document.run {
            getElementById("console") ?: body!!.appendElement("div") { id = "console" }
        }.also { console.attachTo(it, debug = true) }
    }

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
        onConnect {
            console.info("MQTT", "Connecting...")
            subscribe("dt/netmon/home/scan") { qos = 1 }
            subscribe("dt/netmon/home/host") { qos = 1 }
        }

        messages
            .filter { (topic, _, _) -> topic == "dt/netmon/home/scan" }
            .decode<Event.ScanEvent>() handledBy scanEventsStore.process

        messages
            .filter { (topic, _, _) -> topic == "dt/netmon/home/host" }
            .decode<Event.HostEvent>() handledBy hostEventsStore.process

        onError { console.error("MQTT", it) }
        onDisconnect { console.warn("MQTT", "Disconnection packet received from broker", it) }
        onClose { console.warn("MQTT", "Disconnected") }
    }

    render("#app") {
        panel(
            name = "Networks",
            icon = SolidHeroIcons.share
        ) {
            scanEventsStore.data.renderEach({ "${it.network}-${it.timestamp}" }) { (_, network, hosts, timestamp) ->
                subPanel(
                    name = network.hostname,
                    icon = SolidHeroIcons.server
                ) {
                    flow {
                        while (true) {
                            delay(1.seconds)
                            emit(Unit)
                        }
                    }.render {
                        pills("CIDR" to network.cidr.toString(), "Interface" to network.`interface`, "Scan" to timestamp.toMomentString())
                    }
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

                    tree(hosts.map { host -> HostNode(host) })
                }
            }
        }
    }
}
