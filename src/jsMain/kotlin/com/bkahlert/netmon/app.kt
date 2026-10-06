package com.bkahlert.netmon

import com.bkahlert.netmon.ui.OwnedRender
import com.bkahlert.netmon.ui.brokerMessages
import com.bkahlert.netmon.ui.metricsPayloads
import com.bkahlert.netmon.ui.mqttMessageFlow
import com.bkahlert.netmon.ui.networks
import com.bkahlert.netmon.ui.renderOwned
import com.bkahlert.netmon.ui.scans
import com.bkahlert.netmon.ui.status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import mqtt.MqttMessage
import org.w3c.dom.HTMLElement
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Mounts the status and network displays in [statusTarget] and [networksTarget].
 *
 * Uses [messages] when provided, or connects to the production broker otherwise. Uses [clock] for display time and
 * freshness checks. Calls [onSuccess] after the first network scan renders. Returns a [DisplayApp] that owns the
 * mounted displays and their subscriptions.
 */
suspend fun app(
    statusTarget: HTMLElement,
    networksTarget: HTMLElement,
    messages: Flow<MqttMessage>? = null,
    clock: Clock = Clock.System,
    onSuccess: () -> Unit = {},
): DisplayApp {
    val startedAt = clock.now()
    val appJob = Job()
    val messagesInScope = brokerMessages(CoroutineScope(appJob)) { topics -> messages ?: mqttMessageFlow(topics) }
    val currentTime = CurrentTimeStore(UiSettings.REFRESH_INTERVAL, clock, Job(appJob))
    val minuteClock = CurrentTimeStore(1.minutes, clock, Job(appJob))
    val renders = mutableListOf<OwnedRender>()

    try {
        renders += renderOwned(statusTarget) {
            val consoleLogStore = ConsoleLogStore("info" to "Starting...", job = Job(appJob))
            val kioskStatsStore = KioskStatsStore(messagesInScope.metricsPayloads(), job = Job(appJob), clock = clock::now)
            status(consoleLogStore, kioskStatsStore.data, currentTime.data, startedAt)
        }

        renders += renderOwned(networksTarget) {
            val scanEventsStore = ScanEventsStore(
                ScanEventSettings.outdatedThreshold,
                clock,
                currentTime.data,
                Job(appJob),
            )
            messagesInScope.scans().handledBy(scanEventsStore.process)

            var success = false
            scanEventsStore.data handledBy {
                if (!success) {
                    success = true
                    onSuccess()
                }
            }

            networks(scanEventsStore, currentTime.data, minuteClock.data)
        }

        return DisplayApp(appJob, renders)
    } catch (failure: Throwable) {
        withContext(NonCancellable) {
            renders.asReversed().forEach { it.dispose() }
            appJob.cancelAndJoin()
        }
        throw failure
    }
}
