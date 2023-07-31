package com.bkahlert.netmon.ui

import com.bkahlert.kommons.time.Now
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.ticks
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.w3c.dom.HTMLElement
import kotlin.time.Duration

fun RenderContext.scan(
    scan: Event.ScanEvent,
): Tag<HTMLElement> = div(
    classes(
        "space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "bg-white/10 sm:border sm:border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
    )
) {
    val (_, network, hosts, timestamp) = scan
    div("flex items-center justify-center sm:justify-start gap-x-2") {
        icon("shrink-0 w-6 h-6", SolidHeroIcons.server)
        div("text-xl font-bold") { +network.hostname }
        div("flex-grow") {
            ul("flex flex-col items-end text-xs") {
                li {
                    span("font-semibold") { +network.cidr.toString() }
                    +" on "
                    span("font-semibold") { +network.`interface` }
                }
                li {
                    span("font-semibold") {
                        ticks(Settings.WebDisplay.REFRESH_INTERVAL).map {
                            timestamp.coerceAtMost(Now).toMomentString()
                        }.render(into = this) { +it }
                    }
                }
            }
        }
    }

    div("overflow-y-auto") {
        ul("grid grid-cols-[repeat(auto-fit,minmax(0,150px))] justify-between gap-4") {
            hosts.forEach { host ->
                li { host(host) }
            }
        }
    }
}

fun RenderContext.host(host: Host) {
    val duration: Flow<Duration?> = ticks(Settings.WebDisplay.REFRESH_INTERVAL).map {
        host.since?.let { (Now - it).takeIf { diff -> diff >= Duration.ZERO } ?: Duration.ZERO } // coerceAtLeast not working...
    }
    div("flex justify-center sm:justify-start gap-x-2 truncate") {
        className(duration.map {
            when {
                it == null -> ""
                it < Settings.WebDisplay.STATE_CHANGE_STRONG_HIGHLIGHT_DURATION -> "animate-pulse [animation-duration:1s]"
                it < Settings.WebDisplay.STATE_CHANGE_HIGHLIGHT_DURATION -> "animate-pulse"
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
