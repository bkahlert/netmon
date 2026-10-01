package com.bkahlert.netmon

import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.booleans.shouldBeFalse
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.nio.file.Paths
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
        val process = ProcessBuilder(
            Paths.get(System.getProperty("java.home"), "bin", "java").pathString,
            "-Dlogback.configurationFile=$logbackConfiguration",
            "-DLOG_FILE=${logFile.pathString}",
            "-cp", System.getProperty("java.class.path"),
            checkNotNull(kClass.qualifiedName) { "$kClass has no qualified name" },
            *arguments,
        ).also {
            it.directory(workingDirectory.toFile())
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
            throw AssertionError("Test should have completed within $timeout")
        }

        process.isAlive.shouldBeFalse()

        return logFile.readLogMessages()
    }

    @AfterTest
    fun tearDownWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

    private val logbackConfiguration: String =
        checkNotNull(AbstractIntegrationTest::class.java.classLoader.getResource("logback-integration-test.xml")) {
            "logback-integration-test.xml not found"
        }.toExternalForm()
}


@Serializable
data class LogMessage(val level: Level, val message: String) {
    enum class Level { DEBUG, INFO, WARN, ERROR }
}

fun Path.readLogMessages(): List<LogMessage> = useLines { lines ->
    lines.map { JsonFormat.decodeFromString<LogMessage>(it) }.toList()
}
