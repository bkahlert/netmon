package com.bkahlert.netmon.ui

import com.bkahlert.netmon.uri.DataUri
import com.bkahlert.netmon.CurrentTimeStore
import com.bkahlert.netmon.Event.ScanEvent
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.Link
import com.bkahlert.netmon.MinuteClock
import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.ScanEventsStore
import com.bkahlert.netmon.UiSettings
import com.bkahlert.netmon.getElapsedTime
import com.bkahlert.netmon.hosts
import com.bkahlert.netmon.model_identification.DeviceIcons
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.uri.Uri
import dev.fritz2.core.HtmlTag
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Store
import dev.fritz2.core.joinClasses
import dev.fritz2.core.mapByElement
import dev.fritz2.core.mapByKey
import kotlinx.browser.document
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLUListElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

fun RenderContext.networks(scanEventsStore: ScanEventsStore) {
    div("h-full grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] auto-rows-[minmax(0,1fr)] gap-4") {
        scanEventsStore.data
            .map { it.keys.toList() }
            .renderEach(into = this) { source ->
                scan(source, scanEventsStore.mapByKey(source))
            }
    }
}

fun RenderContext.scan(
    source: EventSource,
    events: Store<ScanEvent>,
): HtmlTag<HTMLElement> = div(
    joinClasses(
        "flex flex-col min-h-0 min-w-0 space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "sm:border sm:border-white/20",
        "overflow-hidden",
    ),
) {
    meta(source, events)

    val hosts = events.map(ScanEvent.hosts())

    div("scan__hosts") {
        inlineStyle(cells(hosts.current))
        inlineStyle(hosts.data.map(::cells))
        hosts(hosts, slowClock = MinuteClock.data)
    }
}

/**
 * The time passed since this host changed its status, following [clock] while it is below a minute and shown in
 * seconds, then [slowClock]; a single `null` for a host without a status change.
 *
 * A clock replays its latest tick on collection. That tick of [slowClock] can be a minute old, so it is skipped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Host.elapsedTimes(clock: Flow<Instant>, slowClock: Flow<Instant>): Flow<Duration?> = flow {
    if (since == null) {
        emit(null)
        return@flow
    }
    clock.transformWhile { now ->
        val elapsedTime = getElapsedTime(now)
        emit(elapsedTime)
        elapsedTime != null && elapsedTime < 1.minutes
    }.collect(this)
    slowClock.drop(1).collect { emit(getElapsedTime(it)) }
}

private fun cells(hosts: List<Host>): String = "--cells: ${hosts.size}"

private fun HtmlTag<HTMLElement>.meta(
    source: EventSource,
    events: Store<ScanEvent>,
    datedThreshold: Duration = ScanEventSettings.datedThreshold,
) {
    val timePassed = CurrentTimeStore.data
        .combine(events.data.map { it.timestamp }) { now, timestamp ->
            (now - timestamp).coerceAtLeast(ZERO)
        }

    div("flex items-center justify-center sm:justify-start gap-x-2") {
        icon("shrink-0 w-6 h-6", SFSymbols.`wave.3.left`) {
            className(timePassed.map { radarClass(it, datedThreshold = datedThreshold) })
        }
        div("text-xl font-bold") { +source.node }
        icon("shrink-0 w-6 h-6", SFSymbols.`wave.3.right`) {
            className(timePassed.map { radarClass(it, datedThreshold = datedThreshold) })
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

/** Renders the [hosts] as a grid of cards that the stylesheet sizes by the number of cells the enclosing `.scan__hosts` carries. */
fun RenderContext.hosts(
    hosts: Store<List<Host>>,
    clock: Flow<Instant> = CurrentTimeStore.data,
    slowClock: Flow<Instant> = clock,
): HtmlTag<HTMLUListElement> = ul("hosts") {
    hosts.data.renderEach(Host::ip, into = this) { value ->
        li { host(hosts.mapByElement(value, Host::ip), clock, slowClock) }
    }
}

fun RenderContext.host(
    host: Store<Host>,
    clock: Flow<Instant>,
    slowClock: Flow<Instant>,
    highlightDuration: Duration = UiSettings.HOST_STATE_CHANGE_HIGHLIGHT_DURATION,
) {

    val elapsedTime: Flow<Duration?> = host.data.flatMapLatest { it.elapsedTimes(clock, slowClock) }

    val ips = host.data.map { it.ip }.distinctUntilChanged()
    val hostNames = host.data.map { it.name }.distinctUntilChanged()
    val statuses = host.data.map { it.status }.distinctUntilChanged()

    val models = host.data.map { it.model }.distinctUntilChanged()
    val modelNames = models.map { it?.let(DeviceModelCodes::description) ?: it }
    val kinds = host.data.map { it.kind }.distinctUntilChanged()
    val icons = host.data.map { hostIcon(it.model, it.vendor, it.name, it.kind) }.distinctUntilChanged()
    val links = host.data.map { it.link to it.speed }.distinctUntilChanged()

    val vendors = host.data.map { it.vendor }.distinctUntilChanged()
    val macs = host.data.map { it.mac }.distinctUntilChanged()

    val captions = combine(hostNames, modelNames, kinds, macs) { h, m, k, mac -> h?.substringBefore(".") ?: m ?: k?.label ?: mac?.takeLast(8) }

    div("host") {
        className(elapsedTime.map { if (it != null && it < highlightDuration) "host--highlighted" else "" })
        attr("data-status", statuses.map { it?.toString()?.lowercase() ?: "" })

        div("host__aside") {
            icon("host__icon w-full", icons)
            modelNames.render {
                if (it != null) div("host__model") { fitted(it, length = it.longestWord()) }
            }
        }

        div("host__body") {
            captions.render {
                if (it != null) div("host__name") { fitted(it) }
                else div("host__name") { +"❔" }
            }
            vendors.render {
                if (it != null) div("host__vendor") { fitted(it) }
                else div("host__vendor italic") { fitted("<unknown vendor>") }
            }
            ips.render {
                div("host__ip font-mono") { fitted(it.toString()) }
            }
            links.render { (link, speed) ->
                if (link != null) {
                    div("host__link") {
                        icon("host__link-icon", flowOf(linkIcon(link)))
                        span { +(speed?.toString() ?: if (link == Link.WIFI) "Wi-Fi" else "Ethernet") }
                    }
                }
            }
            statuses.render { status ->
                if (status != null) {
                    div("host__status") {
                        +status.toString()
                        // One text node that is written, not rendered anew, as every tick of a fresh card would launch and replace one.
                        val since = document.createTextNode("")
                        domNode.appendChild(since)
                        elapsedTime
                            .map { it?.toMomentString(descriptive = false)?.let { moment -> " since $moment" }.orEmpty() }
                            .onEach { since.data = it }
                            .launchIn(MainScope() + job)
                    }
                }
            }
        }
    }
}

/** The icon of a host: a known Apple model code's SF Symbol, else the first brand matcher, else the kind's symbol, else the display glyph. */
fun hostIcon(model: String?, vendor: String?, name: String?, kind: Kind?): Uri =
    (model?.takeIf { APPLE_SHAPE.matches(it) }?.let(DeviceModelCodes::symbol) ?: DeviceIcons.specificSymbol(vendor, model, name) ?: kind?.let(DeviceIcons::kindSymbol))
        ?.let(DataUri::svg) ?: SFSymbols.display

private val APPLE_SHAPE = Regex("[A-Za-z]+\\d+(?:,\\d+)?")

private fun linkIcon(link: Link): Uri =
    DeviceIcons.symbol(if (link == Link.WIFI) "mdi:wifi" else "mdi:ethernet")?.let(DataUri::svg) ?: SFSymbols.display
