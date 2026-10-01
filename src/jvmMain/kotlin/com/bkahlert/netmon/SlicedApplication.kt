package com.bkahlert.netmon

import com.bkahlert.netmon.logging.SLF4J
import net.logstash.logback.argument.StructuredArguments.entries
import net.logstash.logback.argument.StructuredArguments.v
import org.slf4j.Logger
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * An application that starts a worker for each slice returned by
 * the specified [slices] with each worker calling
 * the specified [process] repeatedly until
 * either [terminate] is called or the virtual machine is shut down.
 *
 * The [slices] is called in the specified [updateInterval]
 * to update the slices to be processed.
 */
class SlicedApplication<T>(
    slice: () -> Iterable<T>,
    private val updateInterval: Duration = 5.seconds,
    start: (T) -> Unit = {},
    finalize: (T) -> Unit = {},
    private val process: (T) -> Unit,
) {

    private val logger: Logger by SLF4J
    private val state: AtomicReference<SlicedApplicationState> =
        AtomicReference(SlicedApplicationState.Initial(logger, slice, updateInterval, start, process, finalize))

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
        private val start: (T) -> Unit,
        private val process: (T) -> Unit,
        private val finalize: (T) -> Unit,
    ) : SlicedApplicationState {
        fun start(): Started<T> {
            logger.debug("Starting {}...", this)
            return Started(logger, slice, updateInterval, start, process, finalize).also {
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
        private val start: (T) -> Unit,
        private val process: (T) -> Unit,
        private val finalize: (T) -> Unit,
    ) : SlicedApplicationState {

        private var workers: Map<T, Worker> = emptyMap()
        private val failed = Collections.synchronizedSet(mutableSetOf<T>())

        private val manager = thread(name = "manager", start = true) {
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
                        logger.debug("Evicting {}...", v("workers", toBeEvicted.values))
                        toBeEvicted.values.forEach(Started<T>.Worker::interrupt)
                        toBeEvicted.values.forEach(Started<T>.Worker::join)
                        logger.info("Evicted {}", v("workers", toBeEvicted.values))
                    }
                }
                .keys

            val startedWorkers: Map<T, Worker> = requiredSlices.minus(currentSlices).associateWith { Worker(it).apply { start() } }

            workers = workers - evictedSlices + startedWorkers
            logger.debug(
                "Updated workers to {}",
                entries(
                    mapOf(
                        "started" to startedWorkers.keys,
                        "existing" to workers.keys - startedWorkers.keys,
                        "evicted" to evictedSlices,
                    )
                )
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
            Runtime.getRuntime().addShutdownHook(Thread(this::terminate))
        }

        inner class Worker(
            private val value: T,
        ) : Thread("worker:$value") {
            override fun run() {
                start.invoke(value)
                logger.info("Started {}", v("worker", toString()))

                while (!interrupted()) {
                    try {
                        logger.debug("Executing {}...", v("worker", toString()))
                        process.invoke(value)
                        logger.info("Executed {}", v("worker", toString()))
                    } catch (e: InterruptedException) {
                        finalize.invoke(value)
                        logger.info("Terminated {} due to interruption", v("worker", toString()))
                        return
                    } catch (e: Throwable) {
                        failed.add(value)
                        finalize.invoke(value)
                        logger.error("Terminated {} due to failure", v("worker", toString()), e)
                        return
                    }
                }

                finalize.invoke(value)
                logger.info("Terminated {}", v("worker", toString()))
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
