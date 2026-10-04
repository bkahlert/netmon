package com.bkahlert.netmon

import com.bkahlert.netmon.ui.brokerMessages
import com.bkahlert.netmon.ui.metricsPayloads
import com.bkahlert.netmon.ui.networks
import com.bkahlert.netmon.ui.scans
import com.bkahlert.netmon.ui.status
import dev.fritz2.core.render
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import mqtt.MqttMessage

/**
 * Starts a new app instance that renders from the broker's [messages]
 * - the status with the kiosk's figures using the element selected by the specified [statusSelector], and
 * - the network of the scans using the element selected by the specified [networksSelector].
 *
 * An optional [onSuccess] callback can be specified that is called when the app
 * successfully rendered the first network.
 */
fun app(
    statusSelector: String = "#root.app .status",
    networksSelector: String = "#root.app .networks",
    messages: Flow<MqttMessage> = brokerMessages(MainScope()),
    onSuccess: () -> Unit = {},
) {
    render(statusSelector) {
        val consoleLogStore = ConsoleLogStore("info" to "Starting...")
        status(consoleLogStore, KioskStatsStore(messages.metricsPayloads()).data)
    }

    render(networksSelector) {
        val scanEventsStore = ScanEventsStore()

        messages.scans().handledBy(scanEventsStore.process)

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
