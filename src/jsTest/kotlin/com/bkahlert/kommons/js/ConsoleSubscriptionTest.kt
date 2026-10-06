package com.bkahlert.kommons.js

import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

class ConsoleSubscriptionTest {

    @Test
    fun original_invocation_preserves_arguments_receiver_and_return_value() {
        val console = recordingConsole()
        val original = console.asDynamic().log
        val observations = mutableListOf<Pair<String, List<dynamic>>>()
        val subscription = console.observe(listOf("log")) { name, arguments ->
            observations += name to arguments.toList()
        }

        val result = console.asDynamic().log("message", 42)

        (result === console.asDynamic()) shouldBe true
        (console.asDynamic().calls.length as Int) shouldBe 1
        (console.asDynamic().calls[0][0] as String) shouldBe "log"
        (console.asDynamic().calls[0][1][0] as String) shouldBe "message"
        (console.asDynamic().calls[0][1][1] as Int) shouldBe 42
        observations shouldContainExactly listOf("log" to listOf("message", 42))

        subscription.dispose()

        (console.asDynamic().log === original) shouldBe true
    }

    @Test
    fun disposing_one_listener_keeps_the_other_listener_active() {
        val console = recordingConsole()
        val first = mutableListOf<List<dynamic>>()
        val second = mutableListOf<List<dynamic>>()
        val firstSubscription = console.observe(listOf("log")) { _, arguments ->
            first.add(arguments.toList())
        }
        val secondSubscription = console.observe(listOf("log")) { _, arguments ->
            second.add(arguments.toList())
        }

        console.asDynamic().log("before")
        firstSubscription.dispose()
        console.asDynamic().log("after")

        first shouldContainExactly listOf(listOf("before"))
        second shouldContainExactly listOf(listOf("before"), listOf("after"))
        (console.asDynamic().calls.length as Int) shouldBe 2

        secondSubscription.dispose()
    }

    @Test
    fun repeated_disposal_does_not_restore_a_stale_wrapper() {
        val console = recordingConsole()
        val original = console.asDynamic().log
        val first = console.observe(listOf("log")) { _, _ -> }
        val secondObservations = mutableListOf<List<dynamic>>()
        val second = console.observe(listOf("log")) { _, arguments ->
            secondObservations.add(arguments.toList())
        }
        val dispatcher = console.asDynamic().log

        first.dispose()
        first.dispose()
        (console.asDynamic().log === dispatcher) shouldBe true
        console.asDynamic().log("still observed")

        second.dispose()
        first.dispose()

        (console.asDynamic().log === original) shouldBe true
        secondObservations shouldContainExactly listOf(listOf("still observed"))
        (console.asDynamic().calls.length as Int) shouldBe 1
    }

    @Test
    fun cancelling_flow_collection_disposes_its_observation() = runTest {
        val console = recordingConsole()
        val original = console.asDynamic().log
        val observations = mutableListOf<Pair<String, List<dynamic>>>()
        val collector = CoroutineScope(job).launch(start = CoroutineStart.UNDISPATCHED) {
            console.tee("log").collect { (name, arguments) ->
                observations.add(name to arguments.toList())
            }
        }
        withTimeout(5_000) {
            while (console.asDynamic().log === original) yield()
        }

        console.asDynamic().log("before cancellation")
        yield()
        collector.cancelAndJoin()
        console.asDynamic().log("after cancellation")

        observations shouldContainExactly listOf("log" to listOf("before cancellation"))
        (console.asDynamic().log === original) shouldBe true
        (console.asDynamic().calls.length as Int) shouldBe 2
    }
}

internal fun recordingConsole(): Console = js(
    "(function() { var record = function(name) { return function() { this.calls.push([name, Array.from(arguments)]); return this; }; }; return { calls: [], error: record('error'), warn: record('warn'), info: record('info'), log: record('log'), debug: record('debug') }; })()"
).unsafeCast<Console>()
