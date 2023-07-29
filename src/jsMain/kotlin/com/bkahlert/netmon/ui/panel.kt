@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui

import com.bkahlert.kommons.uri.Uri
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

public fun RenderContext.panel(
    classes: String? = null,
    name: String,
    icon: Uri? = null,
    content: ContentBuilder<HTMLDivElement>? = null,
): Tag<HTMLElement> = div(
    classes(
        "space-y-5 py-4 sm:px-4 sm:rounded-xl",
        "bg-white/10 border border-white/20",
        classes,
    )
) {
    div("flex items-center justify-center sm:justify-start gap-x-2") {
        icon("shrink-0 w-6 h-6", icon ?: SolidHeroIcons.swatch)
        div("text-xl font-bold") { +name }
    }

    div("grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4") {
        content?.invoke(this)
    }
}

public fun RenderContext.panel(
    name: String,
    icon: Uri? = null,
    content: ContentBuilder<HTMLDivElement>? = null,
): Tag<HTMLElement> = panel(null, name, icon, content)


public fun RenderContext.subPanel(
    classes: String? = null,
    name: String,
    icon: Uri? = null,
    content: ContentBuilder<HTMLDivElement>? = null,
): Tag<HTMLElement> = div(
    classes(
        "space-y-5 py-4 sm:px-4 sm:rounded-xl",
        "bg-white/10 border border-white/20",
        "grid grid-rows-[1fr_minmax(1px,100%)]",
        classes,
    )
) {
    div("flex items-center justify-center sm:justify-start gap-x-2") {
        icon("shrink-0 w-6 h-6", icon ?: SolidHeroIcons.swatch)
        div("text-xl font-bold") { +name }
    }

    div("overflow-y-auto") {
        div { content?.invoke(this) }
    }
}

public fun RenderContext.subPanel(
    name: String,
    icon: Uri? = null,
    content: ContentBuilder<HTMLDivElement>? = null,
): Tag<HTMLElement> = subPanel(null, name, icon, content)
