package com.bkahlert.netmon

import com.bkahlert.netmon.ui.networks
import com.bkahlert.netmon.ui.scanFlow
import com.bkahlert.netmon.ui.status
import dev.fritz2.core.render
import kotlinx.coroutines.flow.Flow

/**
 * Starts a new app instance that renders
 * - the status using the element selected by the specified [statusSelector], and
 * - the network using the element selected by the specified [networksSelector].
 *
 * An optional [onSuccess] callback can be specified that is called when the app
 * successfully rendered the first network.
 */
fun app(
    statusSelector: String = "#root.app .status",
    networksSelector: String = "#root.app .networks",
    scans: Flow<Pair<EventSource, Event.ScanEvent>> = scanFlow(),
    onSuccess: () -> Unit = {},
) {
    render(statusSelector) {
        val consoleLogStore = ConsoleLogStore("info" to "Starting...")
        status(consoleLogStore, KioskStatsStore().data)
    }

    render(networksSelector) {
        val scanEventsStore = ScanEventsStore()

        scans.handledBy(scanEventsStore.process)

        var success = false
        scanEventsStore.data handledBy {
            if (!success) {
                success = true
                onSuccess()
            }
        }

        networks(scanEventsStore)
    }
}
