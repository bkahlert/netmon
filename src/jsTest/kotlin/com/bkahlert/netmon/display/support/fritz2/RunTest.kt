package com.bkahlert.netmon.display.support.fritz2

import dev.fritz2.core.WithJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.promise

fun <T> runTest(block: suspend WithJob.() -> T): dynamic = MainScope().promise {
    val lifetime = Job()
    try {
        block(object : WithJob {
            override val job: Job = lifetime
        })
    } finally {
        withContext(NonCancellable) { lifetime.cancelAndJoin() }
    }
}
