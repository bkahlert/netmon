package com.bkahlert.netmon.net

import com.bkahlert.kommons.js.Console
import com.bkahlert.kommons.js.DefaultConsoleLogFormatter
import com.bkahlert.kommons.js.format
import com.bkahlert.kommons.js.tee
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.ticks
import com.bkahlert.netmon.timePassed
import dev.fritz2.core.RootStore
import kotlinx.coroutines.flow.map

/** Store of network scans. */
class ScanEventsStore : RootStore<List<Event.ScanEvent>>(emptyList()) {
    val process = handle<Event.ScanEvent> { scans, scan ->
        if (scans.none { it.network == scan.network }) scans + scan
        else scans.map { if (it.network == scan.network) scan else it }
    }

    val cleanUp = handle { scans ->
        val outdated = scans.filter { it.timePassed > Settings.WebDisplay.SCAN_OUTDATED_THRESHOLD }.map { it.network }
        if (outdated.isEmpty()) {
            scans
        } else {
            com.bkahlert.kommons.js.console.debug("Removing outdated scans: $outdated")
            scans.filterNot { it.network in outdated }
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
