package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.scanner.support.logging.SLF4J
import org.slf4j.Logger
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
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
 * Each worker is opened on its slice thread by [open], [SliceWorker.process] is
 * called repeatedly until [terminate] is called or the virtual machine shuts
 * down, and [SliceWorker.close] is called once after processing stops.
 *
 * [slice] is called every [updateInterval] to update the slices to process.
 */
class SlicedApplication<T>(
    slice: () -> Iterable<T>,
    private val updateInterval: Duration = 5.seconds,
    private val open: (T) -> SliceWorker,
) {

    private val logger: Logger by SLF4J
    private val state: AtomicReference<SlicedApplicationState> =
        AtomicReference(SlicedApplicationState.Initial(logger, slice, updateInterval, open))

    val started: Boolean
        get() = state.get() is SlicedApplicationState.Started<*>
    val terminated: Boolean
        get() = state.get() is SlicedApplicationState.Terminated<*>

    @Suppress("UNCHECKED_CAST")
    fun start(): SlicedApplicationState.Started<T> = state.updateAndGet { currentState ->
        when (currentState) {
            is SlicedApplicationState.Initial<*> -> currentState.start()
            is SlicedApplicationState.Started<*> -> currentState.also { logger.warn("Already started {}", it) }
            is SlicedApplicationState.Terminated<*> -> error("Already terminated $currentState")
        }
    } as SlicedApplicationState.Started<T>

    @Suppress("UNCHECKED_CAST")
    fun terminate(): SlicedApplicationState.Terminated<T> = state.updateAndGet { currentState ->
        when (currentState) {
            is SlicedApplicationState.Initial<*> -> error("Can't terminate. Never started $currentState")
            is SlicedApplicationState.Started<*> -> currentState.terminate()
            is SlicedApplicationState.Terminated<*> -> currentState.also { logger.warn("Already terminated {}", it) }
        }
    } as SlicedApplicationState.Terminated<T>
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
                Thread.sleep(10) // give the manager a chance to have the workers updated and be contained in the start log
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

        private var workers: Map<T, Worker> = emptyMap()
        private val failed = Collections.synchronizedSet(mutableSetOf<T>())

        private val manager = thread(name = "manager", start = false) {
            while (!Thread.interrupted()) {
                updateWorkers()
                try {
                    Thread.sleep(updateInterval.inWholeMilliseconds)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        fun waitForTermination(): Terminated<T> {
            logger.info("Waiting for {} to terminate...", this)
            manager.join()
            workers.values.forEach(Started<T>.Worker::interrupt)
            workers.values.forEach(Started<T>.Worker::join)
            return Terminated(slices = workers.keys, failed = failed)
        }

        private fun updateWorkers() {
            logger.debug("Updating workers...")
            val requiredSlices: Iterable<T> = slice.invoke()
            val currentSlices: Set<T> = workers.keys
            val evictedSlices: Set<T> = workers.filterKeys { it !in requiredSlices }
                .also { toBeEvicted ->
                    if (toBeEvicted.isNotEmpty()) {
                        logger.debug("Evicting {}...", toBeEvicted.values)
                        toBeEvicted.values.forEach(Started<T>.Worker::interrupt)
                        toBeEvicted.values.forEach(Started<T>.Worker::join)
                        logger.info("Evicted {}", toBeEvicted.values)
                    }
                }
                .keys

            val startedWorkers: Map<T, Worker> = requiredSlices.minus(currentSlices).associateWith { Worker(it).apply { start() } }

            workers = workers - evictedSlices + startedWorkers
            logger.debug(
                "Updated workers to started={}, existing={}, evicted={}",
                startedWorkers.keys,
                workers.keys - startedWorkers.keys,
                evictedSlices,
            )
        }

        fun terminate(): Terminated<T> {
            logger.debug("Terminating {}...", this)
            manager.interrupt()
            manager.join()
            if (workers.values.isNotEmpty()) {
                workers.values.forEach(Started<T>.Worker::interrupt)
                workers.values.forEach(Started<T>.Worker::join)
            }
            return Terminated(slices = workers.keys, failed = failed).also { logger.info("Terminated {}", it) }
        }

        override fun toString(): String =
            "${SlicedApplication::class.simpleName}(state=${this::class.simpleName}, updateInterval=$updateInterval, slices=${workers.keys})"

        init {
            manager.start()
        }

        inner class Worker(
            private val value: T,
        ) : Thread("worker:$value") {
            override fun run() {
                var worker: SliceWorker? = null
                var failure: Throwable? = null
                try {
                    worker = open.invoke(value)
                    logger.info("Started {}", toString())

                    while (!interrupted()) {
                        logger.debug("Executing {}...", toString())
                        worker.process()
                        logger.info("Executed {}", toString())
                    }
                } catch (e: InterruptedException) {
                } catch (e: Throwable) {
                    failure = e
                }

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
                    logger.error("Terminated {} due to failure", toString(), workerFailure)
                    manager.interrupt()
                } else {
                    logger.info("Terminated {} due to interruption", toString())
                }
            }

            override fun toString(): String = "${this::class.simpleName}($value)"
        }
    }

    data class Terminated<T>(
        val slices: Set<T>,
        val failed: Set<T>,
    ) : SlicedApplicationState {
        override fun toString(): String = "${SlicedApplication::class.simpleName}(state=${this::class.simpleName}, slices=$slices, failed=$failed)"
    }
}
