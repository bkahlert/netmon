package com.bkahlert.netmon.net

import com.bkahlert.netmon.Event
import dev.fritz2.core.RootStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    val outdated = handle<Event.ScanEvent> { scans, scan ->
        scans.mapNotNull { if (it.network == scan.network) null else it }
    }
}

/** Somewhat hacky store to keep track of the uptime, and to update UI elements that present relative time information. */
class UptimeStore(val updateInterval: Duration = 1.seconds) : RootStore<Duration>(Duration.ZERO) {
    init {
        flow {
            while (true) {
                delay(updateInterval)
                emit(Unit)
            }
        }
            .onEach { update(current + updateInterval) }
            .launchIn(MainScope() + job)
    }
}
