package com.bkahlert.netmon.fritz2

import dev.fritz2.core.RootStore
import dev.fritz2.core.WithJob
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import kotlin.test.Test

class StoresTest {

    @Test
    fun partition_initial() = runTest {
        val store = RootStore(listOf("bar"), job = job)
        val partitionedStores = store.partition { it.first() < 'f' }

        partitionedStores should { (first, second) ->
            first.current.shouldContainExactly("bar")
            second.current.shouldBeEmpty()
        }
    }

    @Test
    fun partition_downstream() = runTest {
        val store = RootStore(listOf("bar"), job = job)
        val partitionedStores = store.partition { it.first() < 'f' }

        store.update(listOf("foo", "bar", "baz"))

        delay(50)
        partitionedStores.should { (first, second) ->
            first.current.shouldContainExactly("bar", "baz")
            second.current.shouldContainExactly("foo")
        }
    }

    @Test
    fun partition_upstream() = runTest {
        val store = RootStore(listOf("bar"), job = job)
        val partitionedStores = store.partition { it.first() < 'f' }

        partitionedStores.first.update(listOf("bar", "e", "z"))
        partitionedStores.second.update(listOf("faz", "a"))

        delay(50)
        store.current.shouldContainExactly("bar", "e", "faz")
    }
}

fun <T> runTest(block: suspend WithJob.() -> T): dynamic = MainScope().promise {
    delay(50)
    block(object : WithJob {
        override val job: Job = Job()
    })
    delay(50)
}
