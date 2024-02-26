@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.fritz2

import dev.fritz2.core.Listener
import dev.fritz2.core.subscribe
import org.w3c.dom.Element
import org.w3c.dom.Window
import org.w3c.dom.events.Event

/**
 * A number in the range [0..1], that describes what percentage of the element's scroll height can be displayed.
 * A value of `1` signifies that no vertical scrolling is needed.
 */
public val Element.verticalScrollCoverageRatio: Double
    get() = (clientHeight / scrollHeight.toDouble()).coerceIn(0.0, 1.0)

public val Window.resizes: Listener<Event, Window>
    get() = subscribe("resize")
