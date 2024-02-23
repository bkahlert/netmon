package com.bkahlert.netmon.ui

import com.bkahlert.kommons.time.Now
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.kommons.uri.toUriOrNull
import com.bkahlert.netmon.CurrentTimeStore
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.HostEventSettings
import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.UiSettings
import com.bkahlert.netmon.fritz2.lensForFirst
import com.bkahlert.netmon.fritz2.lensForSecond
import com.bkahlert.netmon.getTimePassed
import com.bkahlert.netmon.model_identification.DeviceModelCodes
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
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

fun RenderContext.scan(
    source: EventSource,
    events: Store<Event.ScanEvent>,
    stabilizedThreshold: Duration = HostEventSettings.stabilizedThreshold,
): HtmlTag<HTMLElement> = div(
    classes(
        "space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "sm:border sm:border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
        "overflow-y-hidden",
    ),
) {
    meta(source, events)

    val now = Now
    val stableAndUnstableHosts: Store<Pair<List<Host>, List<Host>>> = events.map(
        lensOf(
            id = "hosts",
            getter = { it.hosts.partition { host -> host.getTimePassed(now)?.let { passed -> passed > stabilizedThreshold } ?: true } },
            setter = { _, _ -> error("Setting ${Event.ScanEvent::hosts} not supported") },
        )
    )

    div("flex flex-col") {
        hosts(stableAndUnstableHosts.map(lensForSecond()))
        stableAndUnstableHosts.data.mapLatest { (stable, unstable) -> stable.isNotEmpty() && unstable.isNotEmpty() }.render {
            if (it) {
                div("divider-xs opacity-60") {
                    +"$stabilizedThreshold+ unchanged"
                }
            }
        }
        hosts(stableAndUnstableHosts.map(lensForFirst()), classes = "opacity-50 [zoom:0.75]")
    }
}

private fun HtmlTag<HTMLDivElement>.meta(
    source: EventSource,
    events: Store<Event.ScanEvent>,
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
): HtmlTag<HTMLDivElement> = div("overflow-y-auto") {
    zoomedToFitClientHeight() // TODO to improve performance, perform all zooms inside a single requestAnimationFrame
    hosts.data.map { it.size }.distinctUntilChanged() handledBy { resetZoomed() }
    ul(classes("grid grid-cols-[repeat(auto-fill,150px)] justify-around gap-4", classes)) {
        hosts.data.renderEach(Host::ip, into = this) { value ->
            li { host(hosts.mapByElement(value, Host::ip)) }
        }
    }
}

fun RenderContext.host(
    host: Store<Host>,
    strongHighlightDuration: Duration = UiSettings.HOST_STATE_CHANGE_STRONG_HIGHLIGHT_DURATION,
    highlightDuration: Duration = UiSettings.HOST_STATE_CHANGE_HIGHLIGHT_DURATION,
) {

    val timePassed: Flow<Duration?> = CurrentTimeStore.data.combine(host.data) { now, h -> h.getTimePassed(now) }

    val ips = host.data.map { it.ip }.distinctUntilChanged()
    val hostNames = host.data.map { it.name }.distinctUntilChanged()
    val statuses = host.data.map { it.status }.distinctUntilChanged()

    val models = host.data.map { it.model }.distinctUntilChanged()
    val modelNames = models.map { it?.let(DeviceModelCodes::description) ?: it }
    val modelIcons = models.map { it?.let(DeviceModelCodes::icon)?.source?.toUriOrNull() ?: SFSymbols.display }

    val vendors = host.data.map { it.vendor }.distinctUntilChanged()

    val captions = hostNames.combine(modelNames) { h, m -> h?.substringBefore(".") ?: m }


    div("flex justify-center sm:justify-start gap-x-2") {
        className(timePassed.map {
            when {
                it == null -> ""
                it < strongHighlightDuration -> "animate-pulse [animation-duration:1s]"
                it < highlightDuration -> "animate-pulse"
                else -> ""
            }
        })

        div("shrink-0 w-10") {
            icon("w-full", modelIcons) {
                className(statuses.map {
                    when (it) {
                        is Status.UP -> "text-green-500"
                        is Status.DOWN -> "text-red-500"
                        is Status.UNKNOWN -> "text-yellow-500"
                        else -> ""
                    }
                })
            }
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
                else div("text-xs italic") { +"<unknown vendor>" }
            }
            ips.render {
                div("text-xs font-mono") { +it.toString() }.zoomToFitClientWidth()
            }
            statuses.render { status ->
                if (status != null) {
                    div("text-xs") {
                        +status.toString()
                        timePassed
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
