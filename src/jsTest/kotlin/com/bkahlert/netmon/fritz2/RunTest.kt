package com.bkahlert.netmon.fritz2

import dev.fritz2.core.WithJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise

fun <T> runTest(block: suspend WithJob.() -> T): dynamic = MainScope().promise {
    delay(50)
    block(object : WithJob {
        override val job: Job = Job()
    })
    delay(50)
}
