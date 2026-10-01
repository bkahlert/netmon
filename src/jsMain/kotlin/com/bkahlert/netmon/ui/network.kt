package com.bkahlert.netmon.ui

import com.bkahlert.netmon.uri.toUriOrNull
import com.bkahlert.netmon.CurrentTimeStore
import com.bkahlert.netmon.Event.ScanEvent
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.HostEventSettings
import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.ScanEventsStore
import com.bkahlert.netmon.UiSettings
import com.bkahlert.netmon.fritz2.partition
import com.bkahlert.netmon.fritz2.resizes
import com.bkahlert.netmon.getElapsedTime
import com.bkahlert.netmon.hosts
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import dev.fritz2.core.HtmlTag
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Store
import dev.fritz2.core.joinClasses
import dev.fritz2.core.mapByElement
import dev.fritz2.core.mapByKey
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

fun RenderContext.networks(scanEventsStore: ScanEventsStore) {
    div("h-full overflow-y-hidden sm:grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-4") {
        window.resizes.debounce(.5.seconds) handledBy { resetZoomed() }
        val sources = scanEventsStore.data.map { it.keys.toList() }
        sources.map { it.size }.distinctUntilChanged() handledBy { resetZoomed() }
        sources
            .renderEach(into = this) { source ->
                scan(source, scanEventsStore.mapByKey(source))
            }
    }
}

fun RenderContext.scan(
    source: EventSource,
    events: Store<ScanEvent>,
    stabilizedThreshold: Duration = HostEventSettings.stabilizedThreshold,
): HtmlTag<HTMLElement> = div(
    joinClasses(
        "space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "sm:border sm:border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
        "overflow-y-hidden",
    ),
) {
    meta(source, events)

    val (unstableHosts, stableHosts) = events
        .map(ScanEvent.hosts())
        .partition { host ->
            when (val elapsedTime = host.getElapsedTime()) {
                null -> false // = always online / never missing during scan
                else -> elapsedTime <= stabilizedThreshold
            }
        }

    div("flex flex-col") {
        hosts(unstableHosts)
        unstableHosts.data.map { it.isNotEmpty() }
            .combine(stableHosts.data.map { it.isNotEmpty() }) { a, b ->
                a && b
            }.render {
                if (it) {
                    div("divider-xs opacity-60") {
                        +"$stabilizedThreshold+ unchanged"
                    }
                }
            }
        hosts(stableHosts, classes = "opacity-50 [zoom:0.75]")
    }
}

private fun HtmlTag<HTMLDivElement>.meta(
    source: EventSource,
    events: Store<ScanEvent>,
    datedThreshold: Duration = ScanEventSettings.datedThreshold,
) {
    val timePassed = CurrentTimeStore.data
        .combine(events.data.map { it.timestamp }) { now, timestamp ->
            (now - timestamp).coerceAtLeast(ZERO)
        }
    val scanIsDatedFlow = timePassed.map { it > datedThreshold }

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
                        timePassed
                            .map { (-it).toMomentString() }
                            .render(into = this) { +it }
                    }
                }
            }
        }
    }
}

fun RenderContext.hosts(
    hosts: Store<List<Host>>,
    classes: String? = null,
): HtmlTag<HTMLDivElement> = div {
    // "clip" is like "hidden" but with a margin to not cut-off animated content
    inlineStyle("overflow: clip; overflow-clip-margin: 100px; overflow-y: hidden;")
    zoomedToFitClientHeight()
    hosts.data.map { it.size }.distinctUntilChanged() handledBy { resetZoomed() }

    ul(joinClasses("hosts grid grid-cols-[repeat(auto-fill,185px)] justify-around", classes)) {
        hosts.data.renderEach(Host::ip, into = this) { value ->
            li { host(hosts.mapByElement(value, Host::ip)) }
        }
    }
}

fun RenderContext.host(
    host: Store<Host>,
    highlightDuration: Duration = UiSettings.HOST_STATE_CHANGE_HIGHLIGHT_DURATION,
) {

    val elapsedTime: Flow<Duration?> = CurrentTimeStore.data.combine(host.data) { now, h -> h.getElapsedTime(now) }

    val ips = host.data.map { it.ip }.distinctUntilChanged()
    val hostNames = host.data.map { it.name }.distinctUntilChanged()
    val statuses = host.data.map { it.status }.distinctUntilChanged()

    val models = host.data.map { it.model }.distinctUntilChanged()
    val modelNames = models.map { it?.let(DeviceModelCodes::description) ?: it }
    val modelIcons = models.map { it?.let(DeviceModelCodes::icon)?.source?.toUriOrNull() ?: SFSymbols.display }

    val vendors = host.data.map { it.vendor }.distinctUntilChanged()

    val captions = hostNames.combine(modelNames) { h, m -> h?.substringBefore(".") ?: m }

    div("host flex justify-center sm:justify-start gap-x-2") {
        className(elapsedTime.map { if (it != null && it < highlightDuration) "host--highlighted" else "" })
        attr("data-status", statuses.map { it?.toString()?.lowercase() ?: "" })

        div("shrink-0 w-10") {
            icon("host__icon w-full", modelIcons)
            modelNames.render {
                if (it != null) div("opacity-60 text-sm leading-none text-center mt-1") { +it }.zoomToFitClientWidth()
            }
        }

        div("truncate") {
            captions.render {
                if (it != null) div("text-sm font-bold") { +it }.zoomToFitClientWidth()
                else div("text-sm font-bold") { +"❔" }
            }
            vendors.render {
                if (it != null) div("text-xs") { +it }.zoomToFitClientWidth()
                else div("text-xs italic") { +"<unknown vendor>" }.zoomToFitClientWidth()
            }
            ips.render {
                div("text-xs font-mono") { +it.toString() }.zoomToFitClientWidth()
            }
            statuses.render { status ->
                if (status != null) {
                    div("text-xs") {
                        +status.toString()
                        elapsedTime
                            .map { it?.toMomentString(descriptive = false) }
                            .render {
                                if (it != null) {
                                    +" since "
                                    +it
                                }
                            }
                    }
                }
            }
        }
    }
}
