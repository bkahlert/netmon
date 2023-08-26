package com.bkahlert.netmon.exec

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.exec.ShellScript
import io.kotest.assertions.until.until
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ExecutablesKtTest {

    @Test
    fun auto_killing_wrapped_executable_terminates_normally() = runTest {
        val pidFile = createTempFile()
        val javaProcess = CommandLine(
            AutoKillingTestHelper::class,
            // language=sh
            """
            echo $$ >'${pidFile}'
            """.trimIndent(),
        ).start()
        until(5.seconds) { pidFile.exists() && pidFile.readText().isNotBlank() }
        until(5.seconds) { !pidFile.isRunning }
        until(5.seconds) { !javaProcess.isAlive }
    }

    @Test
    fun auto_killing_wrapped_executable_is_killed_on_shutdown() = runTest {
        val pidFile = createTempFile()
        val javaProcess = CommandLine(
            AutoKillingTestHelper::class,
            // language=sh
            """
            echo $$ >'${pidFile}'
            while true; do sleep 1; done
            """.trimIndent(),
        ).start()
        until(5.seconds) { pidFile.exists() && pidFile.readText().isNotBlank() }
        pidFile.isRunning.shouldBeTrue()
        javaProcess.isAlive.shouldBeTrue()
        javaProcess.destroy()
        until(5.seconds) { !javaProcess.isAlive }
        until(5.seconds) { !pidFile.isRunning }
    }
}

private val Path.isRunning: Boolean
    get() = readText().trim().toInt().isRunning
private val Int.isRunning: Boolean
    get() = ShellScript("kill -0 $this 2>/dev/null").exec().exitCode == 0

private fun CommandLine.start(): Process =
    ProcessBuilder(this)
        .apply { redirectErrorStream() }
        .start()
        .apply { thread { inputStream.bufferedReader().readLines().forEach(::println) } }

class AutoKillingTestHelper {
    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            val shellScript = ShellScript(args.first())
            kotlin.runCatching {
                shellScript.autoKilling.exec().readLinesOrThrow().forEach(::println)
            }.onFailure { exitProcess(1) }
        }
    }
}
