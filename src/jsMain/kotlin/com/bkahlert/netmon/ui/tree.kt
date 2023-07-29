@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui

import com.bkahlert.kommons.uri.Uri
import com.bkahlert.netmon.ui.heroicons.SolidHeroIcons
import dev.fritz2.core.RenderContext
import dev.fritz2.core.Tag
import dev.fritz2.core.classes
import dev.fritz2.core.storeOf
import dev.fritz2.headless.components.disclosure
import kotlinx.coroutines.flow.map
import org.w3c.dom.HTMLElement

/** Renders the given [nodes] as a tree. */
fun RenderContext.tree(nodes: List<TreeNode>): Tag<HTMLElement> = ul(
    classes(
        "px-4 py-2.5",
        "overflow-x-hidden overflow-y-auto",
        "rounded-xl",
        "bg-default/60 text-default dark:bg-invert/60 dark:text-invert",
    )
) {
    nodes.forEach { node ->
        li { with(node) { render() } }
    }
}

/** Renders the given [nodes] as a tree. */
fun RenderContext.tree(vararg nodes: TreeNode): Tag<HTMLElement> =
    tree(nodes.asList())


/** A tree node that can be rendered with [tree]. */
public interface TreeNode {

    /** Renders this node and returns created element. */
    fun RenderContext.render(): Tag<HTMLElement>

    /** A node that renders as a collapsable folder. */
    public data class Folder(
        public val title: String,
        public val icon: (open: Boolean) -> Uri = { if (it) SolidHeroIcons.folder_open else SolidHeroIcons.folder },
        public val children: List<TreeNode> = emptyList(),
    ) : TreeNode {
        override fun RenderContext.render(): Tag<HTMLElement> = disclosure {
            openState(storeOf(true))
            disclosureButton("flex items-center gap-2") {
                icon("w-4 h-4", opened.map { open -> icon(open) })
                span("truncate") { +title }
            }
            disclosurePanel(tag = RenderContext::ul) {
                children.forEach { child ->
                    li("ml-6") { with(child) { render() } }
                }
            }
        }
    }

    /** A node that renders as a generic leaf with nothing but a [title] and [icon]. */
    public data class Leaf(
        public val title: String,
        public val icon: Uri = SolidHeroIcons.stop,
    ) : TreeNode {
        override fun RenderContext.render(): Tag<HTMLElement> = a("flex items-center gap-2 truncate") {
            icon("w-4 h-4", icon)
            span("truncate") { +title }
        }
    }
}
