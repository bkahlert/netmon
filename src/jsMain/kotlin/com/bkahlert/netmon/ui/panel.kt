@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui

import com.bkahlert.kommons.uri.Uri
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.net.ScanEventsStore
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

public fun RenderContext.networks(
    scanEventsStore: ScanEventsStore,
    content: RenderContext.(Event.ScanEvent) -> Tag<HTMLElement>,
): Tag<HTMLElement> = div("sm:grid grid-cols-[repeat(auto-fit,minmax(min(15rem,100%),1fr))] gap-4") {
    scanEventsStore.data.renderEach(idProvider = { "${it.network}-${it.timestamp}" }, content = content)
}

public fun RenderContext.panel(
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

public fun RenderContext.panel(
    name: String,
    icon: Uri? = null,
    content: ContentBuilder<HTMLDivElement>? = null,
): Tag<HTMLElement> = panel(null, name, icon, content)
