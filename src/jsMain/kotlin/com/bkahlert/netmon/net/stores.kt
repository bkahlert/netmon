package com.bkahlert.netmon.net

import com.bkahlert.netmon.Event
import dev.fritz2.core.RootStore

/** Store of host updates. */
class HostEventsStore : RootStore<List<Event.HostEvent>>(emptyList()) {
    val process = handle<Event.HostEvent> { events, event ->
        buildList<Event.HostEvent> {
            add(event)
            events.take(2).forEach { add(it) }
        }
    }
}

/** Store of network scans. */
class ScanEventsStore : RootStore<List<Event.ScanEvent>>(emptyList()) {
    val process = handle<Event.ScanEvent> { scans, scan ->
        if (scans.none { it.network == scan.network }) scans + scan
        else scans.map { if (it.network == scan.network) scan else it }
    }
}
