package com.bkahlert.netmon.ui

import com.bkahlert.kommons.time.toMomentString
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import org.w3c.dom.HTMLElement

@Deprecated("delete")
public data class HostNode(
    public val host: Host,
) : TreeNode {
    override fun RenderContext.render(): Tag<HTMLElement> = div("flex items-center justify-center sm:justify-start gap-x-2 truncate") {
        icon("shrink-0 w-10 h-10", SolidHeroIcons.computer_desktop) {
            className(
                when (host.status) {
                    is Status.UP -> "text-green-600"
                    is Status.DOWN -> "text-red-600"
                    is Status.UNKNOWN -> "text-yellow-600"
                    else -> ""
                }
            )
        }
        span("truncate") {
            host.name?.also { div("text-sm font-bold") { +it } }
            div("text-sm") { +host.ip.toString() }
            host.status?.also { status ->
                div("text-xs") {
                    +status.toString()
                    host.since?.also {
                        +" since "
                        +it.toMomentString(descriptive = false)
                    }
                }
            }
        }
    }
}

fun RenderContext.host(host: Host) =
    div("flex items-center justify-center sm:justify-start gap-x-2 truncate debug") {
        icon("shrink-0 w-10 h-10", SolidHeroIcons.computer_desktop) {
            className(
                when (host.status) {
                    is Status.UP -> "text-green-600"
                    is Status.DOWN -> "text-red-600"
                    is Status.UNKNOWN -> "text-yellow-600"
                    else -> ""
                }
            )
        }
        span("truncate") {
            host.name?.also { div("text-sm font-bold") { +it } }
            div("text-sm") { +host.ip.toString() }
            host.status?.also { status ->
                div("text-xs") {
                    +status.toString()
                    host.since?.also {
                        +" since "
                        +it.toMomentString(descriptive = false)
                    }
                }
            }
        }
    }
