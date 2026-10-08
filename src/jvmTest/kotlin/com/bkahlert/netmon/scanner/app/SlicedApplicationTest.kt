package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.scanner.support.logging.SLF4J
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
import java.util.concurrent.ConcurrentHashMap
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
import com.bkahlert.netmon.contract.invoke
import com.bkahlert.netmon.scanner.support.test.AbstractIntegrationTest

class SlicedApplicationTest : AbstractIntegrationTest() {

    @Test
    fun update_state() = runTest {
        forAll(
            row(Slices()),
            row(Slices("foo", "bar")),
        ) { slices ->
            val application = SlicedApplication(slices) { 10.milliseconds.wait() }
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
            process = {
                invocations.processed(it)
                10.milliseconds.wait()
            },
            finalize = { invocations.finalized(it) },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations.keys.shouldContainExactlyInAnyOrder("foo", "bar")
        invocations["foo"] should { fooInvocations ->
            fooInvocations.shouldNotBeEmpty()
            fooInvocations.forAll { fooInvocation -> fooInvocation.thread shouldBe fooInvocations.first().thread }
        }
        invocations["bar"] should { fooInvocations ->
            fooInvocations.shouldNotBeEmpty()
            fooInvocations.forAll { barInvocation -> barInvocation.thread shouldBe fooInvocations.first().thread }
        }
    }

    @Test
    fun start() = runTest {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            start = { invocations.started(it) },
            process = {
                invocations.processed(it)
                10.milliseconds.wait()
            },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations.groupBy { it.slice }.forAll { (_, invocations) ->
            invocations.shouldNotBeEmpty()
            invocations.take(1).forAll { it.shouldBeInstanceOf<Invocations.Invocation.STARTED<*>>() }
        }
    }

    @Test
    fun processing() = runTest {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            process = {
                invocations.processed(it)
                10.milliseconds.wait()
            },
            finalize = { invocations.finalized(it) },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations["foo"] should { fooInvocations ->
            fooInvocations.shouldNotBeEmpty()
            fooInvocations.dropLast(1).forAll { fooInvocation -> fooInvocation.shouldBeInstanceOf<Invocations.Invocation.PROCESSED<*>>() }
        }
        invocations["bar"] should { fooInvocations ->
            fooInvocations.shouldNotBeEmpty()
            fooInvocations.dropLast(1).forAll { barInvocation -> barInvocation.shouldBeInstanceOf<Invocations.Invocation.PROCESSED<*>>() }
        }
    }

    @Test
    fun slice_added() = runTest {
        val invocations = Invocations<String>()
        val slices = Slices("foo")
        val application = SlicedApplication(
            slice = slices,
            updateInterval = 10.milliseconds,
            process = {
                invocations.processed(it)
                10.milliseconds.wait()
            },
            finalize = { invocations.finalized(it) },
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
            process = {
                invocations.processed(it)
                10.milliseconds.wait()
            },
            finalize = { invocations.finalized(it) },
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
            process = {
                invocations.processed(it)
                10.milliseconds.wait()
            },
            finalize = { invocations.finalized(it) },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations.groupBy { it.slice }.forAll { (_, invocations) ->
            invocations.shouldNotBeEmpty()
            invocations.takeLast(1).forAll { it.shouldBeInstanceOf<Invocations.Invocation.FINALIZED<*>>() }
        }
    }

    @Test
    fun wait_for_termination() = runTest {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            process = {
                invocations.processed(it)
                10.milliseconds.wait()
            },
            finalize = { invocations.finalized(it) },
        )

        val started = application.start()
        thread {
            100.milliseconds.wait()
            started.terminate()
        }
        started.waitForTermination()

        invocations.groupBy { it.slice }.forAll { (_, invocations) ->
            invocations.shouldNotBeEmpty()
            invocations.takeLast(1).forAll { it.shouldBeInstanceOf<Invocations.Invocation.FINALIZED<*>>() }
        }
    }

    @Test
    fun wait_for_workers() {
        val invocations = Invocations<String>()
        val busyWorkStarted = CountDownLatch(1)
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            process = {
                if (it == "bar") {
                    busyWorkStarted.countDown()
                    600.milliseconds.busyWait()
                } else {
                    50.milliseconds.wait()
                }
            },
            finalize = { invocations.finalized(it) },
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
            process = { if (it == "bar") throw RuntimeException("test") else 50.milliseconds.wait() },
        )

        application.start()
        100.milliseconds.wait()
        val state = application.terminate()

        state.failed.shouldContainExactly("bar")
    }

    @Test
    fun finalization_failed_worker() {
        val invocations = Invocations<String>()
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            process = { if (it == "bar") throw RuntimeException("test") else 50.milliseconds.wait() },
            finalize = { invocations.finalized(it) },
        )

        application.start()
        100.milliseconds.wait()
        application.terminate()

        invocations["bar"].shouldNotBeEmpty().last().shouldBeInstanceOf<Invocations.Invocation.FINALIZED<*>>()
    }

    @Test
    fun failed_start_ends_the_application() {
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            start = { if (it == "bar") throw RuntimeException("test") },
            process = { 50.milliseconds.wait() },
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
            process = { if (it == "bar") throw RuntimeException("test") else 10.seconds.wait() },
            finalize = { invocations.finalized(it) },
        )

        val started = application.start()
        val state = CompletableFuture.supplyAsync { started.waitForTermination() }.get(5, TimeUnit.SECONDS)

        state.failed.shouldContainExactly("bar")
        invocations["foo"].shouldContainExactly(Invocations.Invocation.FINALIZED("foo"))
    }

    @Test
    fun failed_worker_with_failed_finalization_ends_the_application() {
        val application = SlicedApplication(
            slice = Slices("foo", "bar"),
            process = { if (it == "bar") throw RuntimeException("test") else 50.milliseconds.wait() },
            finalize = { if (it == "bar") throw RuntimeException("finalize") },
        )

        val started = application.start()
        val state = CompletableFuture.supplyAsync { started.waitForTermination() }.get(5, TimeUnit.SECONDS)

        state.failed.shouldContainExactly("bar")
    }

    @Test
    fun removed_slice_closes_its_session_and_reappearance_gets_fresh_resources() {
        val slices = AtomicReference(setOf("lan"))
        val sessions = ConcurrentHashMap<String, NetworkSession>()
        val created = AtomicInteger()
        val sessionCreated = CountDownLatch(2)
        val scansStarted = CountDownLatch(2)
        val firstScanStarted = CountDownLatch(1)
        val firstClosed = CountDownLatch(1)
        val closed = mutableListOf<Int>()
        val holdWorker = CountDownLatch(1)
        val application = SlicedApplication(
            slice = { slices.get() },
            updateInterval = 10.milliseconds,
            start = { slice ->
                val id = created.incrementAndGet()
                sessions[slice] = NetworkSession.open { resources ->
                    resources.own(AutoCloseable {
                        synchronized(closed) { closed += id }
                        if (id == 1) firstClosed.countDown()
                    })
                    testScanner()
                }
                sessionCreated.countDown()
            },
            process = { slice ->
                sessions.getValue(slice).scan()
                firstScanStarted.countDown()
                scansStarted.countDown()
                holdWorker.await()
            },
            finalize = { slice -> sessions.remove(slice)?.close() },
        )

        val started = application.start()
        try {
            firstScanStarted.await(5, TimeUnit.SECONDS) shouldBe true
            slices.set(emptySet())
            firstClosed.await(5, TimeUnit.SECONDS) shouldBe true
            slices.set(setOf("lan"))
            sessionCreated.await(5, TimeUnit.SECONDS) shouldBe true
            scansStarted.await(5, TimeUnit.SECONDS) shouldBe true
        } finally {
            started.terminate()
        }

        synchronized(closed) { closed.toList() } shouldContainExactly listOf(1, 2)
    }

    @Test
    fun worker_cleanup_failure_marks_the_slice_failed() {
        val processStarted = CountDownLatch(1)
        val application = SlicedApplication(
            slice = { listOf("lan") },
            process = {
                processStarted.countDown()
                CountDownLatch(1).await()
            },
            finalize = { throw IllegalStateException("cleanup failed") },
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
                process = { 40.milliseconds.wait() },
                finalize = { logger.info("Finalizing {}", it) },
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

    fun started(slice: T): Boolean = this.add(Invocation.STARTED(slice))
    fun processed(slice: T): Boolean = this.add(Invocation.PROCESSED(slice))
    fun finalized(slice: T): Boolean = this.add(Invocation.FINALIZED(slice))

    val keys: Set<T> get() = this.map { it.slice }.toSet()
    operator fun get(slice: T): List<Invocation<T>> = this.filter { it.slice == slice }

    override fun toString(): String = this.joinToString(", ", "(", ")")

    sealed class Invocation<T>(open val slice: T, val thread: Thread) {
        data class STARTED<T>(override val slice: T) : Invocation<T>(slice, Thread.currentThread())
        data class PROCESSED<T>(override val slice: T) : Invocation<T>(slice, Thread.currentThread())
        data class FINALIZED<T>(override val slice: T) : Invocation<T>(slice, Thread.currentThread())

        override fun toString(): String = "$slice ${this::class.simpleName?.lowercase()} by ${thread.name}"
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Duration.wait(): Unit = Thread.sleep(inWholeMilliseconds)
private fun Duration.busyWait(): Unit = (System.currentTimeMillis() + inWholeMilliseconds).let {
    while (System.currentTimeMillis() < it) {
    }
}
