package com.bkahlert.netmon.scanner.support.exec

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test

class CommandLineTest {

    @Test
    fun output_of_a_succeeding_command() {
        val output = CommandLine("echo", "hello").exec().readTextOrThrow()
        output shouldBe "hello\n"
    }

    @Test
    fun failing_command_throws_with_exit_code_and_error_output() {
        val exit = CommandLine("sh", "-c", "echo oops >&2; exit 3").exec()
        val exception = shouldThrow<IOException> { exit.readTextOrThrow() }
        exception.message should {
            it shouldContain "exit code 3"
            it shouldContain "oops"
        }
    }

    @Test
    fun interruption_reaps_child_while_stdout_is_open() {
        assertInterruptionReapsChild("echo $$ > \"\$1\"; exec sleep 60")
    }

    @Test
    fun interruption_forcibly_reaps_child_ignoring_termination() {
        assertInterruptionReapsChild("trap '' TERM; echo $$ > \"\$1\"; exec sleep 60")
    }

    @Test
    fun already_interrupted_caller_never_receives_an_exit() {
        repeat(100) {
            val result = try {
                Thread.currentThread().interrupt()
                runCatching { CommandLine("true").exec() }
            } finally {
                Thread.interrupted()
            }
            result.exceptionOrNull().shouldBeInstanceOf<InterruptedException>()
        }
    }

    @Test
    fun is_the_list_of_command_and_arguments() {
        val commandLine = CommandLine("nmap", listOf("--privileged", "-sn"))
        commandLine shouldContainExactly listOf("nmap", "--privileged", "-sn")
    }

    @Test
    fun string_form_quotes_arguments_with_whitespace() {
        val string = CommandLine("nmap", "-sn", "10.0.0.0/24", "a b").toString()
        string shouldBe "nmap -sn 10.0.0.0/24 'a b'"
    }

    private fun assertInterruptionReapsChild(script: String) {
        val pidFile = Files.createTempFile(Path.of("build").toAbsolutePath(), "command-line-", ".pid")
        val invocationThreads = ThreadGroup("command-line-${pidFile.fileName}")
        val result = AtomicReference<Result<CommandLine.Exit>>()
        var child: ProcessHandle? = null
        val caller = Thread(invocationThreads, {
            result.set(runCatching {
                CommandLine("sh", "-c", script, "fixture", pidFile.toString()).exec()
            })
        }, "interrupted command caller").apply { start() }
        try {
            val pid = awaitPid(pidFile)
            child = ProcessHandle.of(pid).orElseThrow()
            child.isAlive shouldBe true

            caller.interrupt()
            caller.join(SECONDS.toMillis(5))

            caller.isAlive shouldBe false
            result.get() should {
                it.exceptionOrNull().shouldBeInstanceOf<InterruptedException>()
                it.isSuccess shouldBe false
            }
            child.isAlive shouldBe false
            Thread.getAllStackTraces().keys.filter { it.threadGroup === invocationThreads } shouldContainExactly emptyList()
        } finally {
            try {
                caller.interrupt()
                val ownedChild = child ?: Files.readString(pidFile).trim().toLongOrNull()?.let {
                    ProcessHandle.of(it).orElse(null)
                }
                if (ownedChild != null) {
                    ownedChild.destroyForcibly()
                    ownedChild.onExit().get(5, SECONDS)
                }
                caller.join(SECONDS.toMillis(5))
            } finally {
                Files.deleteIfExists(pidFile)
            }
        }
    }
}

private fun awaitPid(pidFile: Path): Long {
    pidFile.fileSystem.newWatchService().use { watcher ->
        pidFile.parent.register(watcher, ENTRY_MODIFY)
        val deadline = System.nanoTime() + SECONDS.toNanos(5)
        while (true) {
            Files.readString(pidFile).trim().toLongOrNull()?.let { return it }
            val remaining = deadline - System.nanoTime()
            check(remaining > 0) { "Command did not write its PID within 5 seconds" }
            val key = watcher.poll(remaining, java.util.concurrent.TimeUnit.NANOSECONDS)
                ?: error("Command did not write its PID within 5 seconds")
            key.pollEvents()
            key.reset()
        }
    }
}
