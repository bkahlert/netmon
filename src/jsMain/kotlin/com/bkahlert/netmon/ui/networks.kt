package com.bkahlert.netmon.ui

import com.bkahlert.kommons.time.Now
import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.kommons.uri.Uri
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.net.ScanEventsStore
import com.bkahlert.netmon.ticks
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.time.Duration

fun RenderContext.networks(
    scanEventsStore: ScanEventsStore,
    content: RenderContext.(Event.ScanEvent) -> Tag<HTMLElement>,
): Tag<HTMLElement> = div("sm:grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-4") {
    scanEventsStore.data.renderEach(idProvider = { "${it.network}-${it.timestamp}" }, content = content)
}


fun RenderContext.network(
    classes: String? = null,
    name: String,
    icon: Uri? = null,
    extra: ContentBuilder<HTMLDivElement>? = null,
    content: ContentBuilder<HTMLDivElement>? = null,
): Tag<HTMLElement> = div(
    classes(
        "space-y-5 pt-4 sm:pb-4 sm:px-4 sm:rounded-xl",
        "bg-white/10 sm:border sm:border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
        classes,
    )
) {
    div("flex items-center justify-center sm:justify-start gap-x-2") {
        icon("shrink-0 w-6 h-6", icon ?: SolidHeroIcons.swatch)
        div("text-xl font-bold") { +name }
        extra?.let { div("flex-grow") { it(this) } }
    }

    div("overflow-y-auto") {
        div { content?.invoke(this) }
    }
}

fun RenderContext.network(
    name: String,
    icon: Uri? = null,
    content: ContentBuilder<HTMLDivElement>? = null,
): Tag<HTMLElement> = network(null, name, icon, content)

fun RenderContext.host(host: Host) {
    val duration: Flow<Duration?> = ticks().map {
        host.since?.let { (Now - it).takeIf { diff -> diff >= Duration.ZERO } ?: Duration.ZERO } // coerceAtLeast not working...
    }
    div("flex justify-center sm:justify-start gap-x-2 truncate") {
        className(duration.map {
            when {
                it == null -> ""
                it < Settings.WebDisplay.strongHighlightHostChangesFor -> "animate-pulse [animation-duration:1s]"
                it < Settings.WebDisplay.highlightHostChangesFor -> "animate-pulse"
                else -> ""
            }
        })
        div("shrink-0 w-10") {
            icon("w-full", SolidHeroIcons.computer_desktop) {
                className(
                    when (host.status) {
                        is Status.UP -> "text-green-600"
                        is Status.DOWN -> "text-red-600"
                        is Status.UNKNOWN -> "text-yellow-600"
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
