package com.bkahlert.netmon

import com.bkahlert.kommons.js.Console
import com.bkahlert.kommons.js.DefaultConsoleLogFormatter
import com.bkahlert.kommons.js.console
import com.bkahlert.kommons.js.format
import com.bkahlert.kommons.js.tee
import com.bkahlert.netmon.Event.ScanEvent
import dev.fritz2.core.RootStore
import kotlinx.coroutines.flow.map

/** Store of network scans. */
class ScanEventsStore : RootStore<Map<EventSource, ScanEvent>>(emptyMap()) {
    val process = handle<Pair<EventSource, ScanEvent>> { scans, scan ->
        if (scan.second.outdated) {
            console.debug("Ignoring outdated scan by ${scan.first} at ${scan.second.timestamp}")
            scans
        } else {
            console.debug("Adding scan by ${scan.first} at ${scan.second.timestamp}")
            scans + scan
        }
    }

    val cleanUp = handle { scans ->
        val outdated = scans.filterValues { it.outdated }.map { it.key }
        if (outdated.isEmpty()) {
            scans
        } else {
            console.debug("Removing outdated scans: $outdated")
            scans.filterNot { it.key in outdated }
        }
    }

    init {
        ticks(Settings.WebDisplay.REFRESH_INTERVAL) handledBy cleanUp
    }
}

/** Store that is attached to the specified [console] storing log messages of the specified [levels]. */
class ConsoleLogStore(
    initial: Pair<String, String>,
    private vararg val levels: String = arrayOf("error", "warn", "info"),
    private val console: Console = com.bkahlert.kommons.js.console,
) : RootStore<Pair<String, String>>(initial) {

    init {
        console.asDynamic()[initial.first](initial.second)
        console.tee(*levels)
            .map { (fn, args) -> fn to DefaultConsoleLogFormatter.format(args) } handledBy update
    }
}
