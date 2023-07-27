package app

import com.bkahlert.kommons.js.attachTo
import com.bkahlert.kommons.js.catchErrors
import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.uri.fragmentParameters
import com.bkahlert.kommons.uri.queryParameters
import com.bkahlert.kommons.uri.toUri
import dev.fritz2.core.alt
import dev.fritz2.core.render
import dev.fritz2.core.src
import dev.fritz2.core.storeOf
import dev.fritz2.headless.components.toastContainer
import io.ktor.http.ParametersBuilder
import kotlinx.browser.window

@JsModule("./logo.png")
@JsNonModule
private external val logo: String


fun main() {

    window.catchErrors()
    window.document.getElementById("console")?.also { console.attachTo(it, debug = true) }

    // http://localhost:8080/#broker-uri=ws%3A%2F%2Ftest.mosquitto.org%3A8080
    val parameters = window.location.href.toUri().run {
        ParametersBuilder().apply {
            appendAll(queryParameters.also { console.debug("Query parameters %s", it) })
            appendAll(fragmentParameters.also { console.debug("Fragment parameters %s", it) })
        }
    }
    val brokerUrl = parameters["broker-uri"] ?: "ws://test.mosquitto.org:8081"

    val frameworkStore = storeOf(brokerUrl)

    MQTT.connect(brokerUrl).apply {
        onConnect {
            console.info("MQTT", "Connecting...")
            subscribe("dt/netmon/home/scans") { qos = 1 }
            subscribe("dt/netmon/home/updates") { qos = 1 }
        }
        onMessage { topic, message, _ ->
            console.debug("MQTT", "Message received", topic, message)
            when (topic) {
                "dt/netmon/home/scans" -> {
                    val hostCount = message.split("\"ip\"").size + 1
                    showToast(toastContainerDefault) {
                        className("bg-gradient-to-r from-fuchsia-700 to-transparent shadow-xl")
                        +"Received scan with $hostCount devices"
                    }
                    frameworkStore.handle { "$hostCount devices" }
                }

                "dt/netmon/home/updates" -> {
                    showToast(toastContainerDefault) {
                        className("bg-gradient-to-r from-amber-700 to-transparent shadow-xl prose")
                        h2 {
                            +message
                        }
                    }
                }

                else -> console.warn("Got message for topic", topic)
            }
        }
        onError { console.error("MQTT", it) }
        onDisconnect { console.warn("MQTT", "Disconnection packet received from broker", it) }
        onClose { console.warn("MQTT", "Disconnected") }
    }

    render("#app") {
        div("prose-box") {
            div("px-6 py-4 text-white bg-sky-700 border-b border-gray-200 font-bold uppercase") {
                +"Which web-framework do you use?"
            }
            div("p-6 bg-white") {
                +"I'm using: "
                frameworkStore.data.renderText()
                img("animate-pulse [animation-iteration-count:3] h-12") {
                    src(logo)
                    alt("Fritz2 logo")
                }
            }
        }

        div {
            toastContainer(
                toastContainerDefault,
                "absolute top-5 right-5 z-10 flex flex-col gap-2 items-start",
                id = toastContainerDefault
            )

            toastContainer(
                containerImportant,
                "absolute top-5 left-1/2 -translate-x-1/2 z-10 flex flex-col gap-2 items-center",
                id = containerImportant
            )
        }

        div("container mx-auto flex flex-col gap-6 border-2 border-sky-700 rounded p-4") {
            h2("text-lg font-semibold") {
                +"Toast demo"
            }
            div("grid grid-cols-1 md:grid-cols-2 gap-4 max-w-1/2 max-h-1/2") {
                button(
                    """flex justify-center items-center px-4 py-2.5
                    | rounded shadow-sm
                    | border border-transparent
                    | text-sm font-sans text-white
                    | bg-gradient-to-r from-sky-700 to-violet-500
                    | hover:bg-gradient-to-r hover:from-sky-600 hover:to-violet-400
                    | focus:outline-none focus:ring-4""".trimMargin(),
                    id = "btn-toast-default"
                ) {
                    +"Create regular toast"

                    clicks handledBy {
                        showToast(toastContainerDefault) {
                            +"Regular toast"
                            className("bg-gray-500/10 shadow-xl")
                        }
                    }
                }

                button(
                    """flex justify-center items-center px-4 py-2.5
                    | rounded shadow-sm
                    | border border-transparent
                    | text-sm font-sans text-white
                    | bg-gradient-to-r from-fuchsia-700 to-rose-500
                    | hover:bg-gradient-to-r hover:from-fuchsia-600 hover:to-rose-400
                    | focus:outline-none focus:ring-4""".trimMargin(),
                    id = "btn-toast-important"
                ) {
                    +"Create important toast"

                    clicks handledBy {
                        showToast(containerImportant) {
                            className("bg-gradient-to-r from-fuchsia-700 to-rose-500 shadow-xl")
                            div("flex flex-row items-center gap-4 mr-2 text-white") {
                                span { +"⚠️" }
                                div {
                                    div("font-semibold") { +"Important" }
                                    div { +"This toast is rendered in another container" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}