package com.bkahlert.netmon.ui

import com.bkahlert.kommons.time.Now
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.stable
import com.bkahlert.netmon.ticks
import com.bkahlert.netmon.timePassed
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import org.w3c.dom.HTMLElement
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
fun RenderContext.scan(
    source: EventSource,
    events: Flow<Event.ScanEvent>,
): Tag<HTMLElement> = div(
    classes(
        "space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "bg-white/10 sm:border sm:border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
    )
) {
    div("flex items-center justify-center sm:justify-start gap-x-2") {
        icon("shrink-0 w-6 h-6", SolidHeroIcons.server)
        div("text-xl font-bold") { +source.node }
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
                        ticks(Settings.WebDisplay.REFRESH_INTERVAL)
                            .combine(events.map { it.timestamp }) { _, timestamp -> timestamp.coerceAtMost(Now).toMomentString() }
                            .render(into = this) { +it }
                    }
                }
            }
        }
    }

    div("overflow-y-auto") {
        val (stable, recent) = events.mapLatest { it.hosts.partition { it.stable } }.let { it.map { it.first } to it.map { it.second } }
        ul("grid grid-cols-[repeat(auto-fill,150px)] justify-between gap-4") {
            recent.renderEach(into = this) { host ->
                li { host(host) }
            }
        }
        div("divider-xs opacity-50") { +"${Settings.HOST_STATE_CHANGE_STABLE_DURATION}+ unchanged" }
        ul("grid grid-cols-[repeat(auto-fill,150px)] justify-between gap-4 opacity-50") {
            stable.renderEach(into = this) { host ->
                li { host(host) }
            }
        }
    }
}

fun RenderContext.host(host: Host) {
    val duration: Flow<Duration?> = ticks(Settings.WebDisplay.REFRESH_INTERVAL).map { host.timePassed }
    div("flex justify-center sm:justify-start gap-x-2 truncate") {
        className(duration.map {
            when {
                it == null -> ""
                it < Settings.WebDisplay.HOST_STATE_CHANGE_STRONG_HIGHLIGHT_DURATION -> "animate-pulse [animation-duration:1s]"
                it < Settings.WebDisplay.HOST_STATE_CHANGE_HIGHLIGHT_DURATION -> "animate-pulse"
                else -> ""
            }
        })
        div("shrink-0 w-10") {
            icon("w-full", SolidHeroIcons.computer_desktop) {
                className(
                    when (host.status) {
                        is Status.UP -> "text-green-500"
                        is Status.DOWN -> "text-red-500"
                        is Status.UNKNOWN -> "text-yellow-500"
                        else -> ""
                    }
                )
            }
            host.model?.also {
                div("opacity-60 text-[8px] leading-none text-center mt-0.5 truncate") { +it }
            }
            host.vendor?.also {
                div("opacity-60 text-[8px] leading-none text-center mt-0.5 truncate") { +it }
            }
        }
        span("truncate") {
            host.name?.also { div("text-sm font-bold truncate") { +it } }
            div("text-sm") { +host.ip.toString() }
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
            host.services.takeIf { it.isNotEmpty() }?.also { services ->
                ul("opacity-60 text-[8px] leading-none flex flex-wrap gap-1 mt-0.5 [&>*]:") {
                    services.forEach { service ->
                        li("truncate") { +service }
                    }
                }
            }
        }
    }
}
