package com.bkahlert.netmon.ui

import com.bkahlert.kommons.time.Now
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.kommons.uri.toUriOrNull
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.HostEventSettings
import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.UiSettings
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.stable
import com.bkahlert.netmon.ticks
import com.bkahlert.netmon.timePassed
import dev.fritz2.core.HtmlTag
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Store
import dev.fritz2.core.classes
import dev.fritz2.core.lensOf
import dev.fritz2.core.mapByElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

fun RenderContext.scan(
    source: EventSource,
    events: Store<Event.ScanEvent>,
): HtmlTag<HTMLElement> = div(
    classes(
        "space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "bg-white/10 sm:border sm:border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
        "overflow-y-hidden",
    ),
) {
    val scanAgeFlow = ticks(UiSettings.REFRESH_INTERVAL)
        .combine(events.data.map { it.timestamp }) { _, timestamp -> (Now - timestamp).coerceAtLeast(ZERO) }
    val scanIsDatedFlow = scanAgeFlow.map { it > ScanEventSettings.datedThreshold }

    div("flex items-center justify-center sm:justify-start gap-x-2") {
        icon("shrink-0 w-6 h-6", SFSymbols.`wave.3.left`) {
            className(scanIsDatedFlow.map { if (it) "text-yellow-500/60" else "animate-variable-color" })
        }
        div("text-xl font-bold") { +source.node }
        icon("shrink-0 w-6 h-6", SFSymbols.`wave.3.right`) {
            className(scanIsDatedFlow.map { if (it) "text-yellow-500/60" else "animate-variable-color" })
        }
        div("flex-grow") {
            ul("flex flex-col items-end text-xs") {
                li {
                    span("font-semibold") { +source.cidr.toString() }
                    +" on "
                    span("font-semibold") { +source.`interface` }
                }
                li {
                    +"scanned "
                    span("font-semibold") {
                        scanAgeFlow
                            .map { (-it).toMomentString() }
                            .render(into = this) { +it }
                    }
                }
            }
        }
    }

    val hosts: Store<List<Host>> = events.map(lensOf("hosts", { it.hosts }, { p, v -> p.copy(hosts = v) }))

    div("flex flex-col") {
        hosts(hosts) { !it.stable }
        hosts.data.mapLatest { it.any { it.stable } && it.any { !it.stable } }.render {
            if (it) {
                div("divider-xs opacity-60") {
                    +"${HostEventSettings.stabilizedThreshold}+ unchanged"
                }
            }
        }
        hosts(hosts, classes = "opacity-50 [zoom:0.75]") { it.stable }
    }
}

fun RenderContext.hosts(
    hosts: Store<List<Host>>,
    classes: String? = null,
    filter: (Host) -> Boolean = { true }
): HtmlTag<HTMLDivElement> = div("overflow-y-auto") {
    val filteredHosts = hosts.data.map { it.filter(filter) }
    zoomedToFitClientHeight()
    filteredHosts.map { it.size }.distinctUntilChanged() handledBy { resetZoomed() }
    ul(classes("grid grid-cols-[repeat(auto-fill,150px)] justify-around gap-4", classes)) {
        filteredHosts.renderEach(Host::ip, this) { value ->
            li { host(hosts.mapByElement(value, Host::ip)) }
        }
    }
}

fun RenderContext.host(host: Store<Host>) {
    val duration: Flow<Duration?> = ticks(UiSettings.REFRESH_INTERVAL).combine(host.data) { _, h -> h.timePassed }
    val modelName = host.data.map { it.model?.let(DeviceModelCodes::description) ?: it.model }

    div("flex justify-center sm:justify-start gap-x-2") {
        className(duration.map {
            when {
                it == null -> ""
                it < UiSettings.HOST_STATE_CHANGE_STRONG_HIGHLIGHT_DURATION -> "animate-pulse [animation-duration:1s]"
                it < UiSettings.HOST_STATE_CHANGE_HIGHLIGHT_DURATION -> "animate-pulse"
                else -> ""
            }
        })
        div("shrink-0 w-10") {
            val deviceIcon = host.data.map { it.model?.let(DeviceModelCodes::icon)?.source?.toUriOrNull() ?: SFSymbols.display }
            icon("w-full", deviceIcon) {
                className(host.data.map {
                    when (it.status) {
                        is Status.UP -> "text-green-500"
                        is Status.DOWN -> "text-red-500"
                        is Status.UNKNOWN -> "text-yellow-500"
                        else -> ""
                    }
                })
            }
            modelName.render {
                if (it != null) div("opacity-60 text-sm leading-none text-center mt-1") { +it }.zoomToFitClientWidth()
            }
        }
        span("truncate") {
            host.data.combine(modelName) { h, m -> h.name?.substringBefore(".") ?: m }.render { caption ->
                if (caption != null) {
                    div("text-sm font-bold") { +caption }.zoomToFitClientWidth()
                    host.data.map { it.vendor }.render { if (it != null) div("text-sm") { +it }.zoomToFitClientWidth() }
                    div("text-xs font-mono") { host.data.map { it.ip }.renderText(this) }.zoomToFitClientWidth()
                } else {
                    div("text-sm font-mono") { host.data.map { it.ip }.renderText(this) }.zoomToFitClientWidth()
                    host.data.map { it.vendor }.render { if (it != null) div("text-xs") { +it }.zoomToFitClientWidth() }
                }
            }

            host.data.map { it.status }.render { status ->
                if (status != null) {
                    div("text-xs") {
                        +status.toString()
                        duration.render {
                            it?.apply {
                                +" since "
                                +toMomentString(descriptive = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = ""
}
