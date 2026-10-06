package com.bkahlert.netmon

import com.bkahlert.kommons.js.console
import com.bkahlert.netmon.Event.ScanEvent
import dev.fritz2.core.Handler
import dev.fritz2.core.RootStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A store of the latest scan per source, excluding scans older than [outdatedThreshold] according to [clock] and [ticks]. */
class ScanEventsStore(
    private val outdatedThreshold: Duration,
    private val clock: Clock,
    ticks: Flow<Instant>,
    job: Job,
) : RootStore<Map<EventSource, ScanEvent>>(emptyMap(), job = job) {

    val process: Handler<Pair<EventSource, ScanEvent>> = handle { currentScans, (newSource, newScan) ->
        if (newScan.isOutdated()) {
            console.debug("Ignoring outdated scan by $newSource at ${newScan.timestamp}")
            currentScans
        } else {
            console.debug("Adding scan by $newSource at ${newScan.timestamp}")
            currentScans + (newSource to newScan)
        }
    }

    val cleanUp: Handler<Unit> = handle { currentScans ->
        val outdated = currentScans.mapNotNull { (source, scan) -> source.takeIf { scan.isOutdated() } }
        if (outdated.isEmpty()) {
            currentScans
        } else {
            console.debug("Removing outdated scans: $outdated")
            currentScans.filterNot { (source, _) -> source in outdated }
        }
    }

    private fun ScanEvent.isOutdated(): Boolean = (clock.now() - timestamp) > outdatedThreshold

    init {
        ticks.map { } handledBy cleanUp
    }
}
