package com.bkahlert.netmon.ui

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import org.w3c.dom.HTMLElement

public data class HostNode(
    public val host: Host,
) : TreeNode {
    override fun RenderContext.render(): Tag<HTMLElement> = div(
        classes(
            "flex items-center justify-center sm:justify-start gap-x-2",
            "w-48 mt-2.5 ml-2.5 px-4 py-3",
            "rounded shadow-md bg-glass text-default dark:text-invert text-sm",
            "border border-transparent",
            "truncate",
        )
    ) {
        icon("shrink-0 w-12 h-12", SolidHeroIcons.computer_desktop) {
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
            host.name?.also { div("font-bold") { +it } }
            div { +host.ip.toString() }
        }
    }
}

public data class HostUpdatesNode(
    val hosts: List<Host>,
) : TreeNode {
    override fun RenderContext.render(): Tag<HTMLElement> = div(
        "grid grid-cols-[repeat(auto-fit,minmax(min(1rem,100%),1fr))] gap-4 m-4 items-start",
    ) {
        hosts
            .take(2)
            .forEach { host ->
                with(HostUpdateNode(host)) {
                    render()
                }
            }
    }
}

public data class HostUpdateNode(
    public val host: Host,
) : TreeNode {
    override fun RenderContext.render(): Tag<HTMLElement> = div(
        classes(
            "flex items-center justify-center sm:justify-start gap-x-2",
            "w-32 mt-2.5 ml-2.5 px-4 py-3",
            "rounded shadow-md bg-fuchsia-300 text-sm",
            "border border-transparent",
            "debug",
            "truncate",
        )
    ) {
        icon(
            "shrink-0 w-12 h-12", when (host.status) {
                is Status.UP -> SolidHeroIcons.arrow_up_circle
                is Status.DOWN -> SolidHeroIcons.arrow_down_circle
                else -> SolidHeroIcons.question_mark_circle
            }
        ) {
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
            host.name?.also { div("font-bold") { +it } }
            div { +host.ip.toString() }
        }
    }
}
