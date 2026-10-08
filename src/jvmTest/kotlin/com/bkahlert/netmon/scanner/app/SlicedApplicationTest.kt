package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.contract.invoke
import com.bkahlert.netmon.scanner.support.logging.SLF4J
import com.bkahlert.netmon.scanner.support.test.AbstractIntegrationTest
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class SlicedApplicationTest : AbstractIntegrationTest() {

    @Test
    fun update_state() = runTest {
        forAll(
            row(Slices()),
            row(Slices("foo", "bar")),
        ) { slices ->
            val application = SlicedApplication(slice = slices) { worker { 10.milliseconds.wait() } }
            application.started shouldBe false
            application.terminated shouldBe false

            application.start()
            application.started shouldBe true
            application.terminated shouldBe false

            100.milliseconds.wait()
            val state = application.terminate()
            application.started shouldBe false
            application.terminated shouldBe true
            state.failed.shouldBeEmpty()
        }
    }

    @Test
    fun initial_workers() = runTest {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        invocations.processed(slice)
                        10.milliseconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations.keys.shouldContainExactlyInAnyOrder("foo", "bar")
        invocations["foo"] should { fooInvocations ->
            fooInvocations.filterIsInstance<Invocations.Invocation.OPENED<*>>().size shouldBe 1
            fooInvocations.shouldNotBeEmpty()
            fooInvocations.forAll { fooInvocation -> fooInvocation.thread shouldBe fooInvocations.first().thread }
        }
        invocations["bar"] should { barInvocations ->
            barInvocations.filterIsInstance<Invocations.Invocation.OPENED<*>>().size shouldBe 1
            barInvocations.shouldNotBeEmpty()
            barInvocations.forAll { barInvocation -> barInvocation.thread shouldBe barInvocations.first().thread }
        }
    }

    @Test
    fun processing() = runTest {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        invocations.processed(slice)
                        10.milliseconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations["foo"] should { fooInvocations ->
            fooInvocations.filterIsInstance<Invocations.Invocation.PROCESSED<*>>().size shouldBeGreaterThan 1
            fooInvocations.dropLast(1).drop(1).forAll { fooInvocation ->
                fooInvocation.shouldBeInstanceOf<Invocations.Invocation.PROCESSED<*>>()
            }
        }
        invocations["bar"] should { barInvocations ->
            barInvocations.filterIsInstance<Invocations.Invocation.PROCESSED<*>>().size shouldBeGreaterThan 1
            barInvocations.dropLast(1).drop(1).forAll { barInvocation ->
                barInvocation.shouldBeInstanceOf<Invocations.Invocation.PROCESSED<*>>()
            }
        }
    }

    @Test
    fun slice_added() = runTest {
        val invocations = Invocations<String>()
        val slices = Slices("foo")
        val application = SlicedApplication(
            slice = slices,
            updateInterval = 10.milliseconds,
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        invocations.processed(slice)
                        10.milliseconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        application.start()
        100.milliseconds.wait()
        slices.add("bar")
        100.milliseconds.wait()
        application.terminate()

        invocations["foo"].shouldNotBeEmpty().filterIsInstance<Invocations.Invocation.PROCESSED<*>>() should { fooInvocations ->
            invocations["bar"].shouldNotBeEmpty().filterIsInstance<Invocations.Invocation.PROCESSED<*>>() should { barInvocations ->
                fooInvocations.size.shouldBeGreaterThan((barInvocations.size * 1.5).toInt())
            }
        }
    }

    @Test
    fun slice_removed() = runTest {
        val invocations = Invocations<String>()
        val slices = Slices("foo", "bar")
        val application = SlicedApplication(
            slice = slices,
            updateInterval = 10.milliseconds,
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        invocations.processed(slice)
                        10.milliseconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        application.start()
        100.milliseconds.wait()
        slices.remove("bar")
        100.milliseconds.wait()
        application.terminate()

        invocations["foo"].shouldNotBeEmpty().filterIsInstance<Invocations.Invocation.PROCESSED<*>>() should { fooInvocations ->
            invocations["bar"].shouldNotBeEmpty().filterIsInstance<Invocations.Invocation.PROCESSED<*>>() should { barInvocations ->
                fooInvocations.size.shouldBeGreaterThan((barInvocations.size * 1.5).toInt())
            }
        }
    }

    @Test
    fun termination() = runTest {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        invocations.processed(slice)
                        10.milliseconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations.groupBy { it.slice }.forAll { (_, perSliceInvocations) ->
            perSliceInvocations.filterIsInstance<Invocations.Invocation.CLOSED<*>>().size shouldBe 1
            perSliceInvocations.takeLast(1).forAll { it.shouldBeInstanceOf<Invocations.Invocation.CLOSED<*>>() }
        }
    }

    @Test
    fun wait_for_termination() = runTest {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        invocations.processed(slice)
                        10.milliseconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        val started = application.start()
        thread {
            100.milliseconds.wait()
            started.terminate()
        }
        started.waitForTermination()

        invocations.groupBy { it.slice }.forAll { (_, perSliceInvocations) ->
            perSliceInvocations.filterIsInstance<Invocations.Invocation.CLOSED<*>>().size shouldBe 1
            perSliceInvocations.takeLast(1).forAll { it.shouldBeInstanceOf<Invocations.Invocation.CLOSED<*>>() }
        }
    }

    @Test
    fun wait_for_workers() {
        val busyWorkStarted = CountDownLatch(1)
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                worker(
                    process = {
                        if (slice == "bar") {
                            busyWorkStarted.countDown()
                            600.milliseconds.busyWait()
                        } else {
                            50.milliseconds.wait()
                        }
                    },
                )
            },
        )

        measureTime {
            application.start()
            busyWorkStarted.await(5, TimeUnit.SECONDS) shouldBe true
            application.terminate()
        } shouldBeGreaterThan 500.milliseconds
    }

    @Test
    fun failed_workers() {
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                worker {
                    if (slice == "bar") throw RuntimeException("test") else 50.milliseconds.wait()
                }
            },
        )

        application.start()
        100.milliseconds.wait()
        val state = application.terminate()

        state.failed.shouldContainExactly("bar")
    }

    @Test
    fun processing_failure_closes_the_worker() {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        invocations.processed(slice)
                        if (slice == "bar") throw RuntimeException("test") else 10.seconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        val started = application.start()
        val state = CompletableFuture.supplyAsync { started.waitForTermination() }.get(5, TimeUnit.SECONDS)

        state.failed.shouldContainExactly("bar")
        invocations["bar"].takeLast(1).single().shouldBeInstanceOf<Invocations.Invocation.CLOSED<*>>()
    }

    @Test
    fun failed_open_ends_the_application() {
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                if (slice == "bar") throw RuntimeException("test")
                worker { 50.milliseconds.wait() }
            },
        )

        val started = application.start()
        val state = CompletableFuture.supplyAsync { started.waitForTermination() }.get(5, TimeUnit.SECONDS)

        state.failed.shouldContainExactly("bar")
    }

    @Test
    fun failed_worker_ends_the_application() {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                invocations.opened(slice)
                worker(
                    process = {
                        if (slice == "bar") throw RuntimeException("test") else 10.seconds.wait()
                    },
                    close = { invocations.closed(slice) },
                )
            },
        )

        val started = application.start()
        val state = CompletableFuture.supplyAsync { started.waitForTermination() }.get(5, TimeUnit.SECONDS)

        state.failed.shouldContainExactly("bar")
        invocations["foo"].shouldContainExactly(Invocations.Invocation.OPENED("foo"), Invocations.Invocation.CLOSED("foo"))
    }

    @Test
    fun close_failure_on_processing_failure_is_suppressed() {
        val processingFailure = RuntimeException("test")
        val closeFailure = RuntimeException("close")
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            open = { slice ->
                worker(
                    process = {
                        if (slice == "bar") throw processingFailure else 50.milliseconds.wait()
                    },
                    close = { if (slice == "bar") throw closeFailure },
                )
            },
        )

        val started = application.start()
        val state = CompletableFuture.supplyAsync { started.waitForTermination() }.get(5, TimeUnit.SECONDS)

        state.failed.shouldContainExactly("bar")
        processingFailure.suppressed.toList() shouldContainExactly listOf(closeFailure)
    }

    @Test
    fun removed_slice_closes_its_worker_and_reappearance_gets_fresh_resources() {
        val slices = AtomicReference(setOf("lan"))
        val created = AtomicInteger()
        val opened = CountDownLatch(2)
        val firstProcessed = CountDownLatch(1)
        val firstClosed = CountDownLatch(1)
        val holdWorker = CountDownLatch(1)
        val lifecycle = Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
        val application = SlicedApplication(
            slice = { slices.get() },
            updateInterval = 10.milliseconds,
            open = { slice ->
                val id = created.incrementAndGet()
                lifecycle += "opened" to id
                opened.countDown()
                worker(
                    process = {
                        firstProcessed.countDown()
                        holdWorker.await()
                    },
                    close = {
                        lifecycle += "closed" to id
                        if (slice == "lan" && id == 1) firstClosed.countDown()
                    },
                )
            },
        )

        val started = application.start()
        try {
            firstProcessed.await(5, TimeUnit.SECONDS) shouldBe true
            slices.set(emptySet())
            holdWorker.countDown()
            firstClosed.await(5, TimeUnit.SECONDS) shouldBe true
            val eventsAfterFirstClose = synchronized(lifecycle) { lifecycle.toList() }
            eventsAfterFirstClose shouldContainExactly listOf("opened" to 1, "closed" to 1)

            holdWorker.await(0, TimeUnit.SECONDS) shouldBe true
            slices.set(setOf("lan"))
            opened.await(5, TimeUnit.SECONDS) shouldBe true
        } finally {
            started.terminate()
        }

        synchronized(lifecycle) { lifecycle.toList() } shouldContainExactly listOf(
            "opened" to 1,
            "closed" to 1,
            "opened" to 2,
            "closed" to 2,
        )
    }

    @Test
    fun close_failure_marks_the_slice_failed() {
        val processStarted = CountDownLatch(1)
        val application = SlicedApplication(
            slice = { listOf("lan") },
            open = {
                worker(
                    process = {
                        processStarted.countDown()
                        CountDownLatch(1).await()
                    },
                    close = { throw IllegalStateException("cleanup failed") },
                )
            },
        )

        val started = application.start()
        processStarted.await(5, TimeUnit.SECONDS) shouldBe true
        val result = started.terminate()

        result.failed.shouldContainExactly("lan")
    }

    companion object {

        private val logger by SLF4J

        @JvmStatic
        fun main(vararg args: String) {
            println("a")
            logger.info("a")
            val application = SlicedApplication(
                slice = Slices("foo", "bar"),
                open = { worker { 40.milliseconds.wait() } },
            )
            logger.info("c")

            val started = application.start()
            started.waitForTermination()

            logger.info("z")
        }
    }
}

private data class Slices<T>(
    var values: List<T> = emptyList(),
) : () -> List<T> {
    constructor(vararg slices: T) : this(slices.asList())

    override fun invoke(): List<T> = values

    fun add(value: T) {
        values = values + value
    }

    fun remove(value: T) {
        values = values - value
    }
}

private class Invocations<T> : MutableList<Invocations.Invocation<T>> by Collections.synchronizedList(mutableListOf()) {

    fun opened(slice: T): Boolean = this.add(Invocation.OPENED(slice))
    fun processed(slice: T): Boolean = this.add(Invocation.PROCESSED(slice))
    fun closed(slice: T): Boolean = this.add(Invocation.CLOSED(slice))

    val keys: Set<T> get() = this.map { it.slice }.toSet()
    operator fun get(slice: T): List<Invocation<T>> = this.filter { it.slice == slice }

    override fun toString(): String = this.joinToString(", ", "(", ")")

    sealed class Invocation<T>(open val slice: T, val thread: Thread) {
        data class OPENED<T>(override val slice: T) : Invocation<T>(slice, Thread.currentThread())
        data class PROCESSED<T>(override val slice: T) : Invocation<T>(slice, Thread.currentThread())
        data class CLOSED<T>(override val slice: T) : Invocation<T>(slice, Thread.currentThread())

        override fun toString(): String = "$slice ${this::class.simpleName?.lowercase()} by ${thread.name}"
    }
}

private fun worker(
    process: () -> Unit = {},
    close: () -> Unit = {},
): SliceWorker = object : SliceWorker {
    override fun process() = process()
    override fun close() = close()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Duration.wait(): Unit = Thread.sleep(inWholeMilliseconds)
private fun Duration.busyWait(): Unit = (System.currentTimeMillis() + inWholeMilliseconds).let {
    while (System.currentTimeMillis() < it) {
    }
}
