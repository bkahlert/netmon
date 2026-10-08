package com.bkahlert.netmon.contract

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** A link rate in Mbit/s, serialized as that number; must be positive. */
@JvmInline
@Serializable
value class LinkSpeed(val megabitsPerSecond: Int) {
    init {
        require(megabitsPerSecond > 0) { "A link speed must be positive, not $megabitsPerSecond" }
    }

    /** Returns `72 Mbit/s` below a gigabit, else gigabits with at most one decimal, for example `2.5 Gbit/s`. */
    override fun toString(): String {
        if (megabitsPerSecond < 1000) return "$megabitsPerSecond Mbit/s"
        val tenths = (megabitsPerSecond + 50) / 100
        val text = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
        return "$text Gbit/s"
    }
}
