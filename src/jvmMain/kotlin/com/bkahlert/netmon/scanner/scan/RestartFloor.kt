package com.bkahlert.netmon.scanner.scan

import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The earliest instant a host's grace period may start from after a restart.
 *
 * The uptime comes from the monotonic clock, so a wall-clock jump
 * (e.g. when NTP answers on a board that booted without a clock) moves the floor along.
 */
internal class RestartFloor(private val started: TimeMark = TimeSource.Monotonic.markNow()) {

    fun at(scanTime: Instant): Instant = scanTime - started.elapsedNow()
}
