@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon.fritz2

import dev.fritz2.core.Lens
import dev.fritz2.core.lensOf

public fun <A, B> lensForFirst(): Lens<Pair<A, B>, A> = lensOf("first", Pair<A, B>::first) { p, v -> v to p.second }
public fun <A, B> lensForSecond(): Lens<Pair<A, B>, B> = lensOf("second", Pair<A, B>::second) { p, v -> p.first to v }
