package com.bkahlert.netmon

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.exec.environment
import com.bkahlert.kommons.exec.workingDirectory
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.assertions.failure
import io.kotest.matchers.booleans.shouldBeFalse
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString
import kotlin.io.path.useLines
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class AbstractIntegrationTest {

    protected val workingDirectory: Path = createTempDirectory("netmon-integration-test")

    @BeforeTest
    fun setUpWorkingDirectory() {
        workingDirectory.deleteRecursively()
        workingDirectory.createDirectory()
    }

    protected fun runUntilLogged(
        kClass: KClass<*>,
        vararg arguments: String,
        customize: ProcessBuilder.() -> Unit = {},
        timeout: Duration = 25.seconds,
        predicate: (String) -> Boolean,
    ): List<LogMessage> {
        val logFile = workingDirectory.resolve("netmon-integration-test.log")
        val process = CommandLine(kClass, *arguments)
            .let(::ProcessBuilder)
            .also {
                it.workingDirectory = workingDirectory
                it.environment["LOG_FILE"] = logFile.pathString
                it.environment["FILE_LOG_PRESET"] = "json"
                it.customize()
                it.redirectErrorStream(true)
            }.start()

        val outputConsumer = thread {
            process.inputStream.bufferedReader().forEachLine {
                println(it)
                if (predicate(it)) process.destroy()
            }
        }

        val latch = CountDownLatch(1)
        val terminationObserver = thread {
            process.waitFor()
            latch.countDown()
        }
        if (!latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            outputConsumer.interrupt()
            terminationObserver.interrupt()
            process.destroyForcibly()
            throw failure("Test should have completed within $timeout")
        }

        process.isAlive.shouldBeFalse()

        return logFile.readLogMessages()
    }

    @AfterTest
    fun tearDownWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

}


@Serializable
data class LogMessage(val level: Level, val message: String) {
    enum class Level { DEBUG, INFO, WARN, ERROR }
}

fun Path.readLogMessages(): List<LogMessage> = useLines { lines ->
    lines.map { JsonFormat.decodeFromString<LogMessage>(it) }.toList()
}
