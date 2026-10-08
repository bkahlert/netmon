package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.scanner.support.exec.CommandLine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class SlicedApplicationTest {

    @Test
    fun update_state() {
        val application = SlicedApplication(slice = { emptyList<String>() }) { worker() }
        application.started shouldBe false
        application.terminated shouldBe false
        shouldThrow<IllegalStateException> { application.terminate() }
        application.start()
        application.started shouldBe true
        application.terminated shouldBe false
        val result = application.terminate()
        application.started shouldBe false
        application.terminated shouldBe true
        result.slices.shouldBeEmpty()
        result.failed.shouldBeEmpty()
        application.terminate() shouldBe result
        shouldThrow<IllegalStateException> { application.start() }
    }

    @Test
    fun initial_workers() {
        val events = Collections.synchronizedList(mutableListOf<Triple<String, String, Thread>>())
        val processed = CountDownLatch(2)
        val hold = CountDownLatch(1)
        val application = SlicedApplication(slice = { listOf("foo", "bar") }) { slice ->
            events += Triple(slice, "open", Thread.currentThread())
            worker(
                process = {
                    events += Triple(slice, "process", Thread.currentThread())
                    processed.countDown()
                    hold.await()
                },
                close = { events += Triple(slice, "close", Thread.currentThread()) },
            )
        }
        application.start()
        val result = try {
            processed.awaitSignal()
            application.terminate()
        } finally {
            hold.countDown()
            application.terminate()
        }
        result.slices.shouldContainExactlyInAnyOrder("foo", "bar")
        result.failed.shouldBeEmpty()
        for (slice in listOf("foo", "bar")) {
            val lifetime = events.filter { it.first == slice }
            lifetime.map { it.second } shouldContainExactly listOf("open", "process", "close")
            lifetime.map { it.third }.distinct().size shouldBe 1
        }
    }

    @Test
    fun processing() {
        val processedTwice = CountDownLatch(2)
        val application = SlicedApplication(slice = { listOf("lan") }) {
            worker(process = {
                processedTwice.countDown()
                if (processedTwice.count == 0L) CountDownLatch(1).await()
            })
        }
        application.start()
        try {
            processedTwice.awaitSignal()
        } finally {
            application.terminate()
        }
    }

    @Test
    fun slice_added() {
        val slices = AtomicReference(setOf("foo"))
        val fooProcessed = CountDownLatch(1)
        val barProcessed = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val application = SlicedApplication(slice = { slices.get() }, updateInterval = 10.milliseconds) { slice ->
            events += "open:$slice"
            worker(
                process = {
                    (if (slice == "foo") fooProcessed else barProcessed).countDown()
                    CountDownLatch(1).await()
                },
                close = { events += "close:$slice" },
            )
        }
        application.start()
        try {
            fooProcessed.awaitSignal()
            slices.set(setOf("foo", "bar"))
            barProcessed.awaitSignal()
        } finally {
            application.terminate()
        }
        events.take(2) shouldContainExactly listOf("open:foo", "open:bar")
        events.filter { it.startsWith("close:") }.shouldContainExactlyInAnyOrder("close:foo", "close:bar")
    }

    @Test
    fun removed_slice_closes_its_worker_and_reappearance_gets_fresh_resources() {
        val slices = AtomicReference(setOf("lan"))
        val created = AtomicInteger()
        val firstProcessed = CountDownLatch(1)
        val firstClosed = CountDownLatch(1)
        val secondProcessed = CountDownLatch(1)
        val lifecycle = Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
        val application = SlicedApplication(slice = { slices.get() }, updateInterval = 10.milliseconds) {
            val id = created.incrementAndGet()
            lifecycle += "opened" to id
            worker(
                process = {
                    (if (id == 1) firstProcessed else secondProcessed).countDown()
                    CountDownLatch(1).await()
                },
                close = {
                    lifecycle += "closed" to id
                    if (id == 1) firstClosed.countDown()
                },
            )
        }
        application.start()
        try {
            firstProcessed.awaitSignal()
            slices.set(emptySet())
            firstClosed.awaitSignal()
            lifecycle.toList() shouldContainExactly listOf("opened" to 1, "closed" to 1)
            slices.set(setOf("lan"))
            secondProcessed.awaitSignal()
        } finally {
            application.terminate()
        }
        lifecycle.toList() shouldContainExactly listOf("opened" to 1, "closed" to 1, "opened" to 2, "closed" to 2)
    }

    @Test
    fun failed_open_ends_the_application() {
        assertFailure(expectedCloses = 0) { throw IllegalStateException("open") }
    }

    @Test
    fun processing_failure_closes_the_worker() {
        assertFailure { worker(process = { throw IllegalStateException("process") }) }
    }

    @Test
    fun close_failure_on_processing_failure_is_suppressed() {
        val processingFailure = IllegalStateException("process")
        val closeFailure = IllegalStateException("close")
        assertFailure {
            worker(process = { throw processingFailure }, close = { throw closeFailure })
        }
        processingFailure.suppressed.toList() shouldContainExactly listOf(closeFailure)
    }

    @Test
    fun close_failure_marks_the_slice_failed() {
        val processed = CountDownLatch(1)
        val application = SlicedApplication(slice = { listOf("lan") }) {
            worker(
                process = { processed.countDown(); CountDownLatch(1).await() },
                close = { throw IllegalStateException("close") },
            )
        }
        application.start()
        val result = try {
            processed.awaitSignal()
            application.terminate()
        } finally {
            application.terminate()
        }
        result.failed.shouldContainExactly("lan")
    }

    @Test
    fun concurrent_start_opens_each_slice_once() {
        val go = CountDownLatch(1)
        val ready = CountDownLatch(8)
        val opened = CountDownLatch(1)
        val opens = AtomicInteger()
        val closes = AtomicInteger()
        val application = SlicedApplication(slice = { listOf("lan") }) {
            opens.incrementAndGet()
            opened.countDown()
            worker(process = { CountDownLatch(1).await() }, close = { closes.incrementAndGet() })
        }
        val handles = Collections.synchronizedList(mutableListOf<SlicedApplicationState.Started<String>>())
        val callers = List(8) {
            thread {
                ready.countDown()
                go.await()
                handles += application.start()
            }
        }
        try {
            ready.awaitSignal()
            go.countDown()
            callers.forEach { it.join(SECONDS.toMillis(5)); it.isAlive shouldBe false }
            opened.awaitSignal()
            handles.size shouldBe 8
            handles.all { it === handles.first() } shouldBe true
        } finally {
            go.countDown()
            handles.distinctBy { System.identityHashCode(it) }.forEach { it.terminate() }
            application.terminate()
        }
        opens.get() shouldBe 1
        closes.get() shouldBe 1
    }

    @Test
    fun terminate_and_wait_share_final_snapshot() {
        val processed = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val closes = AtomicInteger()
        val application = SlicedApplication(slice = { listOf("lan") }) {
            worker(
                process = { processed.countDown(); CountDownLatch(1).await() },
                close = { closing.countDown(); releaseClose.await(); closes.incrementAndGet() },
            )
        }
        val started = application.start()
        processed.awaitSignal()
        val results = Collections.synchronizedList(mutableListOf<SlicedApplicationState.Terminated<String>>())
        val ready = CountDownLatch(4)
        val callers = List(4) { index ->
            thread {
                ready.countDown()
                results += when (index) {
                    0 -> application.terminate()
                    1 -> started.terminate()
                    else -> started.waitForTermination()
                }
            }
        }
        try {
            processed.awaitSignal()
            ready.awaitSignal()
            closing.awaitSignal()
            results.shouldBeEmpty()
        } finally {
            releaseClose.countDown()
            callers.forEach { it.join(SECONDS.toMillis(5)); it.isAlive shouldBe false }
            application.terminate()
        }
        results.size shouldBe 4
        results.forEach {
            it.slices.shouldContainExactly("lan")
            it.failed.shouldBeEmpty()
            it shouldBe results.first()
        }
        closes.get() shouldBe 1
    }

    @Test
    fun slice_supplier_failure_closes_active_workers() {
        val processed = CountDownLatch(2)
        val closed = CountDownLatch(2)
        val calls = AtomicInteger()
        val application = SlicedApplication(
            slice = {
                if (calls.incrementAndGet() > 1) {
                    processed.await()
                    throw IllegalStateException("supplier")
                }
                listOf("foo", "bar")
            },
            updateInterval = 10.milliseconds,
        ) {
            worker(process = { processed.countDown(); CountDownLatch(1).await() }, close = { closed.countDown() })
        }
        val started = application.start()
        try {
            closed.awaitSignal()
            val result = started.waitForTermination()
            result.slices.shouldContainExactlyInAnyOrder("foo", "bar")
            result.failed.shouldBeEmpty()
        } finally {
            application.terminate()
        }
    }

    @Test
    fun cancelled_factory_releases_partial_resources() {
        val opening = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<Pair<String, Thread>>())
        val application = SlicedApplication(slice = { listOf("lan") }) {
            NetworkSession.open { resources ->
                resources.own(AutoCloseable { events += "first" to Thread.currentThread() })
                resources.own(AutoCloseable { events += "last" to Thread.currentThread() })
                events += "open" to Thread.currentThread()
                opening.countDown()
                CountDownLatch(1).await()
                testScanner()
            }
        }
        application.start()
        val result = try {
            opening.awaitSignal()
            application.terminate()
        } finally {
            application.terminate()
        }
        result.failed.shouldBeEmpty()
        events.map { it.first } shouldContainExactly listOf("open", "last", "first")
        events.map { it.second }.distinct().size shouldBe 1
    }

    @Test
    fun termination_waits_for_noninterruptible_work() {
        val processing = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicInteger()
        val application = SlicedApplication(slice = { listOf("lan") }) {
            worker(
                process = {
                    processing.countDown()
                    while (release.count > 0) {
                        try {
                            release.await()
                        } catch (_: InterruptedException) {
                            interrupted.countDown()
                        }
                    }
                    Thread.currentThread().interrupt()
                },
                close = { CountDownLatch(0).await(); closed.incrementAndGet() },
            )
        }
        application.start()
        processing.awaitSignal()
        val terminated = CompletableFuture<SlicedApplicationState.Terminated<String>>()
        val caller = thread { terminated.complete(application.terminate()) }
        try {
            interrupted.awaitSignal()
            terminated.isDone shouldBe false
            closed.get() shouldBe 0
        } finally {
            release.countDown()
            caller.join(SECONDS.toMillis(5))
            caller.isAlive shouldBe false
        }
        val result = terminated.get(5, SECONDS)
        result.failed.shouldBeEmpty()
        closed.get() shouldBe 1
    }

    @Test
    fun termination_reaps_actual_blocked_command_line_child() {
        val pidFile = Files.createTempFile(Path.of("build").toAbsolutePath(), "slice-command-", ".pid")
        val closes = AtomicInteger()
        val application = SlicedApplication(slice = { listOf("lan") }) {
            worker(
                process = {
                    CommandLine("sh", "-c", "echo $$ > \"\$1\"; exec sleep 60", "fixture", pidFile.toString()).exec()
                },
                close = { closes.incrementAndGet() },
            )
        }
        application.start()
        var child: ProcessHandle? = null
        val terminated = CompletableFuture<SlicedApplicationState.Terminated<String>>()
        var caller: Thread? = null
        try {
            child = ProcessHandle.of(awaitPid(pidFile)).orElseThrow()
            child.isAlive shouldBe true
            caller = thread { terminated.complete(application.terminate()) }
            val result = terminated.get(5, SECONDS)
            result.failed.shouldBeEmpty()
            child.isAlive shouldBe false
            closes.get() shouldBe 1
        } finally {
            child?.takeIf { it.isAlive }?.let { it.destroyForcibly(); it.onExit().get(5, SECONDS) }
            application.terminate()
            caller?.join(SECONDS.toMillis(5))
            Files.deleteIfExists(pidFile)
        }
    }

    @Test
    fun application_owner_closes_publisher_after_worker_cleanup() {
        val processing = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val application = SlicedApplication(slice = { listOf("lan") }) {
            NetworkSession.open { resources ->
                resources.own(AutoCloseable { events += "first" })
                resources.own(AutoCloseable { events += "last" })
                testScanner { processing.countDown(); CountDownLatch(1).await() }
            }
        }
        val owner = ApplicationResources(AutoCloseable { events += "publisher" })
        application.start()
        owner.ownWorkerManager { application.terminate() }
        try {
            processing.awaitSignal()
        } finally {
            owner.close()
        }
        events.toList() shouldContainExactly listOf("last", "first", "publisher")
    }
}

private fun assertFailure(expectedCloses: Int = 1, openFailed: () -> SliceWorker) {
    val siblingProcessing = CountDownLatch(1)
    val siblingCloses = AtomicInteger()
    val failedCloses = AtomicInteger()
    val application = SlicedApplication(slice = { listOf("foo", "bar") }) { slice ->
        if (slice == "bar") {
            siblingProcessing.await()
            val failed = openFailed()
            worker(process = { failed.process() }, close = { failedCloses.incrementAndGet(); failed.close() })
        } else {
            worker(
                process = { siblingProcessing.countDown(); CountDownLatch(1).await() },
                close = { siblingCloses.incrementAndGet() },
            )
        }
    }
    val started = application.start()
    val result = CompletableFuture<SlicedApplicationState.Terminated<String>>()
    val waiter = thread { result.complete(started.waitForTermination()) }
    try {
        val snapshot = result.get(5, SECONDS)
        snapshot.failed.shouldContainExactly("bar")
        siblingCloses.get() shouldBe 1
        failedCloses.get() shouldBe expectedCloses
    } finally {
        application.terminate()
        waiter.join(SECONDS.toMillis(5))
    }
}

private fun worker(process: () -> Unit = {}, close: () -> Unit = {}): SliceWorker = object : SliceWorker {
    override fun process() = process()
    override fun close() = close()
}

private fun CountDownLatch.awaitSignal() {
    await(5, SECONDS) shouldBe true
}

private fun awaitPid(pidFile: Path): Long {
    pidFile.fileSystem.newWatchService().use { watcher ->
        pidFile.parent.register(watcher, ENTRY_MODIFY)
        val deadline = System.nanoTime() + SECONDS.toNanos(5)
        while (true) {
            Files.readString(pidFile).trim().toLongOrNull()?.let { return it }
            val remaining = deadline - System.nanoTime()
            check(remaining > 0) { "Command did not write its PID within 5 seconds" }
            val key = watcher.poll(remaining, NANOSECONDS) ?: error("Command did not write its PID within 5 seconds")
            key.pollEvents()
            key.reset()
        }
    }
}
