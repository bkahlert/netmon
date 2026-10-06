package com.bkahlert.netmon

import dev.fritz2.core.RootStore
import dev.fritz2.core.SimpleHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A store that publishes [clock]'s current time every [refreshInterval]. */
class CurrentTimeStore(
    val refreshInterval: Duration,
    private val clock: Clock,
    job: Job,
) : RootStore<Instant>(clock.now(), job = job) {

    final override val update: SimpleHandler<Instant> = super.update

    init {
        val timeMillis = refreshInterval.inWholeMilliseconds
        flow {
            while (true) {
                delay(timeMillis)
                emit(clock.now())
            }
        } handledBy update
    }
}
