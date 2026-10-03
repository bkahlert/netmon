package com.bkahlert.netmon.ui

import dev.fritz2.core.RenderContext

/**
 * Renders [text] in a span that carries [length] as the custom property `--len`.
 *
 * The stylesheet divides the width of the text column by `--len` to shrink a long line, so the browser needs no
 * measuring; what the shrinking leaves over ends in an ellipsis.
 */
fun RenderContext.fitted(text: String, length: Int = text.length) {
    span("fit") {
        inlineStyle("--len: $length")
        +text
    }
}

/** Returns the length of the longest word of this text, or `0` if it has none. */
fun String.longestWord(): Int = split(' ').maxOfOrNull { it.length } ?: 0
