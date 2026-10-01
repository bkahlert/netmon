package com.bkahlert.netmon.exec

import com.bkahlert.netmon.logging.SLF4J
import java.io.IOException
import kotlin.concurrent.thread

/** A command with its arguments, run synchronously with [exec]. */
class CommandLine(
    val command: String,
    val arguments: List<String>,
) : List<String> by listOf(command) + arguments {

    constructor(command: String, vararg arguments: String) : this(command, arguments.asList())

    /** Runs the command and waits for it to exit. */
    fun exec(): Exit {
        logger.debug("Executing {}", this)
        val process = ProcessBuilder(this).start()
        val error = StringBuilder()
        val errorReader = thread(isDaemon = true, name = "stderr of $command") {
            process.errorStream.bufferedReader().forEachLine { error.appendLine(it) }
        }
        val output = process.inputStream.use { it.readBytes() }
        val exitCode = process.waitFor()
        errorReader.join()
        return Exit(this, exitCode, output, error.toString().trimEnd())
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
