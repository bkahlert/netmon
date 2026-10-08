package com.bkahlert.netmon.display.networks

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import com.bkahlert.netmon.contract.Status
import com.bkahlert.netmon.contract.Host

/** How long a host has been up, in steps from the newest to the oldest; a step holds the times below [below] that no earlier step holds. */
enum class OnlineAge(val token: String, val below: Duration) {
    FIVE_MINUTES("5m", 5.minutes),
    TWENTY_MINUTES("20m", 20.minutes),
    ONE_HOUR("1h", 1.hours),
    TWELVE_HOURS("12h", 12.hours),
    ONE_DAY("24h", 24.hours),
    OLDER("older", Duration.INFINITE);

    companion object {
        /** Returns the step of a host up since [since] at [now]; a [since] after [now] counts as just up. */
        fun of(since: Instant, now: Instant): OnlineAge {
            val up = now - since
            return entries.first { up < it.below }
        }
    }
}

/** Returns how long this host has been up at [now], or `null` if it is not up or has no [Host.since]. */
fun Host.onlineAge(now: Instant): OnlineAge? = since?.takeIf { status == Status.UP }?.let { OnlineAge.of(it, now) }
