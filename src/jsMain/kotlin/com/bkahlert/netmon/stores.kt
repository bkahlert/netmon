@file:Suppress("RedundantVisibilityModifier")

package com.bkahlert.netmon

import com.bkahlert.kommons.js.Console
import com.bkahlert.kommons.js.DefaultConsoleLogFormatter
import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.js.format
import com.bkahlert.kommons.js.tee
import kotlinx.datetime.Clock
import com.bkahlert.netmon.Event.ScanEvent
import dev.fritz2.core.Handler
import dev.fritz2.core.Lens
import dev.fritz2.core.RootStore
import dev.fritz2.core.SimpleHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Duration

/** Store of the current time that updates itself based on the specified [refreshInterval]. */
open class CurrentTimeStore(
    private val refreshInterval: Duration = UiSettings.REFRESH_INTERVAL,
) : RootStore<Instant>(Clock.System.now(), job = Job()) {

    final override val update: SimpleHandler<Instant> = super.update

    init {
        val timeMillis = refreshInterval.inWholeMilliseconds
        flow {
            while (true) {
                delay(timeMillis)
                emit(Clock.System.now())
            }
        } handledBy update
    }

    companion object : CurrentTimeStore()
}

/** Store of network scans. */
class ScanEventsStore(
    private val outdatedThreshold: Duration = ScanEventSettings.outdatedThreshold,
) : RootStore<Map<EventSource, ScanEvent>>(emptyMap(), job = Job()) {

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

    private fun ScanEvent.isOutdated(): Boolean = (Clock.System.now() - timestamp) > outdatedThreshold

    init {
        CurrentTimeStore.data.map { } handledBy cleanUp
    }
}

/** Store that is attached to the specified [console] storing log messages of the specified [levels]. */
class ConsoleLogStore(
    initial: Pair<String, String>,
    private vararg val levels: String = arrayOf("error", "warn", "info"),
    private val console: Console = com.bkahlert.kommons.js.console,
) : RootStore<Pair<String, String>>(initial, job = Job()) {

    init {
        console.asDynamic()[initial.first](initial.second)
        console.tee(*levels)
            .map { (fn, args) -> fn to DefaultConsoleLogFormatter.format(args) }
            .handledBy(update)
    }
}

private object HostsLens : Lens<ScanEvent, List<Host>> {
    override val id: String = "hosts"
    override fun get(parent: ScanEvent): List<Host> = parent.hosts
    override fun set(parent: ScanEvent, value: List<Host>): ScanEvent = parent.copy(hosts = value)
}

fun ScanEvent.Companion.hosts(): Lens<ScanEvent, List<Host>> = HostsLens
