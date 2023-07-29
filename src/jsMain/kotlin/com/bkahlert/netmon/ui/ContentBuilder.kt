@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.ui

import dev.fritz2.core.Tag

public typealias ContentBuilder<C> = Tag<C>.() -> Unit
