package com.bkahlert.netmon

import dev.fritz2.core.RootStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Stores the latest sample from [payloads] that is fresh by [clock], or `null` while none is fresh.
 * Freshness is checked again every [interval].
 */
class KioskStatsStore(
    payloads: Flow<ByteArray>,
    job: Job,
    interval: Duration = KioskStats.INTERVAL,
    clock: () -> Instant = Clock.System::now,
) : RootStore<KioskStats?>(null, job = job) {

    init {
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(interval)
            }
        }
        combine(payloads.map(::kioskStatsOf), ticks) { stats, _ -> stats?.takeIf { it.isFreshAt(clock()) } } handledBy update
    }
}
