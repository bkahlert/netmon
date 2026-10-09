package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.scanner.support.logging.SLF4J
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.slf4j.Logger
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A worker that owns a slice's processing lifecycle.
 *
 * [process] is called repeatedly until processing stops. [close] is called
 * once after processing stops, whether processing ended by interruption or by
 * failure.
 */
interface SliceWorker : AutoCloseable {
    fun process()
}

/**
 * An application that starts one [SliceWorker] per slice returned by [slice].
 *
 * Each worker is opened on an IO thread by [open], [SliceWorker.process] is
 * called repeatedly until [terminate] is called or the virtual machine shuts
 * down, and [SliceWorker.close] is called once after processing stops.
 *
 * Factory, processing, and closure run on the same thread for each lifetime.
 * [slice] is called every [updateInterval] to update the slices to process.
 * A supplier or worker failure stops all workers.
 */
class SlicedApplication<T>(
    slice: () -> Iterable<T>,
    private val updateInterval: Duration = 5.seconds,
    private val open: (T) -> SliceWorker,
) {

    private val logger: Logger by SLF4J
    private val stateLock = Any()
    private val initial = SlicedApplicationState.Initial(logger, slice, updateInterval, open)
    private var startedState: SlicedApplicationState.Started<T>? = null
    private var terminatedState: SlicedApplicationState.Terminated<T>? = null

    val started: Boolean
        get() = synchronized(stateLock) { startedState != null && terminatedState == null }
    val terminated: Boolean
        get() = synchronized(stateLock) { terminatedState != null }

    /**
     * Starts slice reconciliation, or returns the existing started handle.
     * Concurrent calls share one worker manager.
     * @throws IllegalStateException if the application has terminated.
     */
    fun start(): SlicedApplicationState.Started<T> = synchronized(stateLock) {
        check(terminatedState == null) { "Already terminated $terminatedState" }
        startedState ?: initial.start().also { startedState = it }
    }

    /**
     * Stops all workers and returns the shared final snapshot after cleanup.
     * Waits for noninterruptible work to finish.
     * @throws IllegalStateException if the application has never started.
     */
    fun terminate(): SlicedApplicationState.Terminated<T> {
        val started = synchronized(stateLock) {
            terminatedState?.let { return it }
            checkNotNull(startedState) { "Can't terminate. Never started $initial" }
        }
        val result = started.terminate()
        return synchronized(stateLock) {
            terminatedState ?: result.also { terminatedState = it }
        }
    }
}

sealed interface SlicedApplicationState {

    data class Initial<T>(
        private val logger: Logger,
        private val slice: () -> Iterable<T>,
        private val updateInterval: Duration = 5.seconds,
        private val open: (T) -> SliceWorker,
    ) : SlicedApplicationState {
        fun start(): Started<T> {
            logger.debug("Starting {}...", this)
            return Started(logger, slice, updateInterval, open).also {
                logger.info("Started {}", it)
            }
        }

        override fun toString(): String =
            "${SlicedApplication::class.simpleName}(state=${this::class.simpleName}, updateInterval=$updateInterval)"
    }

    data class Started<T>(
        private val logger: Logger,
        private val slice: () -> Iterable<T>,
        private val updateInterval: Duration = 5.seconds,
        private val open: (T) -> SliceWorker,
    ) : SlicedApplicationState {

        private var workers: Map<T, Job> = emptyMap()
        private val failed = Collections.synchronizedSet(mutableSetOf<T>())
        private val root = Job()
        private val scope = CoroutineScope(root + Dispatchers.Default)
        private val snapshotLock = Any()
        private var snapshot: Terminated<T>? = null

        init {
            scope.launch {
                try {
                    while (isActive) {
                        updateWorkers()
                        delay(updateInterval)
                    }
                } catch (_: CancellationException) {
                } catch (failure: Throwable) {
                    logger.error("Terminated slice manager due to failure", failure)
                } finally {
                    root.cancel()
                }
            }
        }

        /** Returns the shared final snapshot after the manager and all workers finish cleanup. */
        fun waitForTermination(): Terminated<T> {
            logger.info("Waiting for {} to terminate...", this)
            runBlocking { root.join() }
            return synchronized(snapshotLock) {
                snapshot ?: Terminated(
                    slices = Collections.unmodifiableSet(workers.keys.toSet()),
                    failed = Collections.unmodifiableSet(synchronized(failed) { failed.toSet() }),
                ).also { snapshot = it }
            }
        }

        private suspend fun updateWorkers() {
            logger.debug("Updating workers...")
            val requiredSlices = slice().toSet()
            val removed = workers.filterKeys { it !in requiredSlices }
            removed.values.forEach { it.cancel() }
            removed.values.forEach { it.join() }
            workers = workers - removed.keys
            for (value in requiredSlices - workers.keys) {
                val job = scope.launch {
                    val workerJob = coroutineContext.job
                    runInterruptible(Dispatchers.IO) { runWorker(value, workerJob) }
                }
                workers = workers + (value to job)
            }
        }

        /** Stops all workers and waits for their cleanup before returning the shared final snapshot. */
        fun terminate(): Terminated<T> {
            logger.debug("Terminating {}...", this)
            root.cancel()
            return waitForTermination().also { logger.info("Terminated {}", it) }
        }

        override fun toString(): String =
            "${SlicedApplication::class.simpleName}(state=${this::class.simpleName}, updateInterval=$updateInterval)"

        private fun runWorker(value: T, job: Job) {
            var worker: SliceWorker? = null
            var failure: Throwable? = null
            try {
                worker = open(value)
                logger.info("Started worker for {}", value)
                while (job.isActive) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Worker thread interrupted")
                    worker.process()
                }
            } catch (workerFailure: InterruptedException) {
                if (!job.isCancelled) failure = workerFailure
            } catch (workerFailure: CancellationException) {
                if (!job.isCancelled) failure = workerFailure
            } catch (workerFailure: Throwable) {
                failure = workerFailure
            } finally {
                // Noninterruptible processing can return with the cancellation interrupt still set.
                Thread.interrupted()
                try {
                    worker?.close()
                } catch (closeFailure: Throwable) {
                    val workerFailure = failure
                    if (workerFailure == null) {
                        failure = closeFailure
                    } else if (workerFailure !== closeFailure) {
                        workerFailure.addSuppressed(closeFailure)
                    }
                }

                val workerFailure = failure
                if (workerFailure != null) {
                    failed.add(value)
                    logger.error("Terminated worker for {} due to failure", value, workerFailure)
                    root.cancel()
                } else {
                    logger.info("Terminated worker for {} due to interruption", value)
                }
            }
        }
    }

    data class Terminated<T>(
        val slices: Set<T>,
        val failed: Set<T>,
    ) : SlicedApplicationState {
        override fun toString(): String = "${SlicedApplication::class.simpleName}(state=${this::class.simpleName}, slices=$slices, failed=$failed)"
    }
}
