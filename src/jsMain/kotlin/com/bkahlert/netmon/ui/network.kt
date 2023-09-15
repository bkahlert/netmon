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
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import org.w3c.dom.HTMLElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

fun RenderContext.scan(
    source: EventSource,
    events: Flow<Event.ScanEvent>,
): Tag<HTMLElement> = div(
    classes(
        "space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "bg-white/10 sm:border sm:border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
        "overflow-y-hidden",
    ),
) {
    val scanAgeFlow = ticks(UiSettings.REFRESH_INTERVAL)
        .combine(events.map { it.timestamp }) { _, timestamp -> (Now - timestamp).coerceAtLeast(ZERO) }
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

    div("overflow-y-auto") {
        zoomedToFitClientHeight()

        val hostsFlows: Flow<Pair<List<Host>, List<Host>>> = events.mapLatest { it.hosts.partition(Host::stable) }
        val stableHostsFlow: Flow<List<Host>> = hostsFlows.map { it.first }
        val recentHostsFlow: Flow<List<Host>> = hostsFlows.map { it.second }

        hostGrid(recentHostsFlow)
        recentHostsFlow.combine(stableHostsFlow) { r, s -> r.isNotEmpty() && s.isNotEmpty() }.render {
            if (it) {
                div("divider-xs opacity-60") {
                    +"${HostEventSettings.stabilizedThreshold}+ unchanged"
                }
            }
        }
        hostGrid(stableHostsFlow, classes = "opacity-50 [zoom:0.75]")
    }
}

fun RenderContext.hostGrid(hosts: Flow<List<Host>>, classes: String? = null) {
    ul(classes("grid grid-cols-[repeat(auto-fill,150px)] justify-around gap-4", classes)) {
        hosts.renderEach(into = this) { host ->
            li { host(host) }
        }
    }
}

fun RenderContext.host(host: Host) {
    val duration: Flow<Duration?> = ticks(UiSettings.REFRESH_INTERVAL).map { host.timePassed }
    div("flex justify-center sm:justify-start gap-x-2") {
        val modelName = host.model?.let { DeviceModelCodes.description(it) } ?: host.model
        className(duration.map {
            when {
                it == null -> ""
                it < UiSettings.HOST_STATE_CHANGE_STRONG_HIGHLIGHT_DURATION -> "animate-pulse [animation-duration:1s]"
                it < UiSettings.HOST_STATE_CHANGE_HIGHLIGHT_DURATION -> "animate-pulse"
                else -> ""
            }
        })
        div("shrink-0 w-10") {
            val deviceIcon = host.model?.let { DeviceModelCodes.icon(it) }?.source?.toUriOrNull() ?: SFSymbols.display
            icon("w-full", deviceIcon) {
                className(
                    when (host.status) {
                        is Status.UP -> "text-green-500"
                        is Status.DOWN -> "text-red-500"
                        is Status.UNKNOWN -> "text-yellow-500"
                        else -> ""
                    }
                )
            }
            modelName?.also {
                div("opacity-60 text-sm leading-none text-center mt-1") { +it }.zoomToFitClientWidth()
            }
        }
        span("truncate") {
            val caption = host.name?.substringBefore(".") ?: modelName
            if (caption != null) {
                div("text-sm font-bold truncate") { +caption }
                host.vendor?.also { div("text-sm") { +it }.zoomToFitClientWidth() }
                div("text-xs font-mono truncate") { +host.ip.toString() }.zoomToFitClientWidth()
            } else {
                div("text-sm font-mono truncate") { +host.ip.toString() }.zoomToFitClientWidth()
                host.vendor?.also { div("text-xs") { +it }.zoomToFitClientWidth() }
            }
            host.status?.also { status ->
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
