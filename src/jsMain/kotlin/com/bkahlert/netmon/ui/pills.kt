@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui

import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import org.w3c.dom.HTMLElement

public fun RenderContext.pills(
    vararg pills: Pair<String, String>,
): Tag<HTMLElement> = dl("flex text-xs font-semibold") {
    pills.forEach { (name, content) ->
        div("flex m-1 border border-slate-500 rounded-full px-2 py-1 gap-2") {
            dt("font-bold") { +name }
            dd { +content }
        }
    }
}
