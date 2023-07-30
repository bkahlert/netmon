package com.bkahlert.netmon

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Returns a [Flow] that emits a unit with the specified [interval]. */
fun ticks(interval: Duration = 1.seconds): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(interval)
    }
}
