package com.bkahlert.netmon.scanner.support.exec

import com.bkahlert.netmon.scanner.support.logging.SLF4J
import java.io.IOException
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread

/** A command with its arguments, run synchronously with [exec]. */
class CommandLine(
    val command: String,
    val arguments: List<String>,
) : List<String> by listOf(command) + arguments {

    constructor(command: String, vararg arguments: String) : this(command, arguments.asList())

    /**
     * Runs the command and waits for it to exit.
     * @throws InterruptedException if interrupted, after the process and its readers have terminated.
     */
    fun exec(): Exit {
        logger.debug("Executing {}", this)
        val process = ProcessBuilder(this).start()
        var output: ByteArray? = null
        var outputFailure: Throwable? = null
        val outputReader = thread(start = false, isDaemon = true, name = "stdout of $command (${process.pid()})") {
            try {
                output = process.inputStream.use { it.readBytes() }
            } catch (failure: Throwable) {
                outputFailure = failure
            }
        }
        val error = StringBuilder()
        var errorFailure: Throwable? = null
        val errorReader = thread(start = false, isDaemon = true, name = "stderr of $command (${process.pid()})") {
            try {
                process.errorStream.bufferedReader().use { reader ->
                    reader.forEachLine { error.appendLine(it) }
                }
            } catch (failure: Throwable) {
                errorFailure = failure
            }
        }
        try {
            outputReader.start()
            errorReader.start()
            val exitCode = process.waitFor()
            outputReader.join()
            errorReader.join()
            outputFailure?.let { throw it }
            process.outputStream.close()
            if (Thread.interrupted()) throw InterruptedException()
            return Exit(this, exitCode, checkNotNull(output), error.toString().trimEnd())
        } catch (failure: Throwable) {
            fun cleanUp(action: () -> Unit) {
                while (true) {
                    try {
                        action()
                        return
                    } catch (interruption: InterruptedException) {
                        failure.addSuppressed(interruption)
                    } catch (cleanupFailure: Throwable) {
                        failure.addSuppressed(cleanupFailure)
                        return
                    }
                }
            }

            cleanUp { process.destroy() }
            cleanUp { process.waitFor(100, MILLISECONDS) }
            cleanUp { if (process.isAlive) process.destroyForcibly() }
            cleanUp { process.waitFor() }
            cleanUp { process.outputStream.close() }
            cleanUp { process.inputStream.close() }
            cleanUp { process.errorStream.close() }
            cleanUp { outputReader.join() }
            cleanUp { errorReader.join() }
            outputFailure?.takeUnless { it === failure }?.let(failure::addSuppressed)
            errorFailure?.takeUnless { it === failure }?.let(failure::addSuppressed)
            throw failure
        }
    }

    override fun toString(): String = joinToString(" ") { if (it.any(Char::isWhitespace)) "'$it'" else it }

    /** What a command wrote and how it exited. */
    class Exit(
        private val commandLine: CommandLine,
        val exitCode: Int,
        private val output: ByteArray,
        val error: String,
    ) {
        /** Returns the standard output if the command succeeded, or throws naming the exit code and the standard error. */
        fun readBytesOrThrow(): ByteArray =
            if (exitCode == 0) output
            else throw IOException("$commandLine terminated with exit code $exitCode" + if (error.isEmpty()) "" else ":\n$error")

        /** Returns the standard output as text if the command succeeded, or throws naming the exit code and the standard error. */
        fun readTextOrThrow(): String = readBytesOrThrow().decodeToString()
    }

    private companion object {
        private val logger by SLF4J
    }
}
