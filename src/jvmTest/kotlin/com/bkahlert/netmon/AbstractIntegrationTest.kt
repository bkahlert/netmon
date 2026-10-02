package com.bkahlert.netmon

import io.kotest.matchers.booleans.shouldBeFalse
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString
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
        timeout: Duration = 120.seconds,
        predicate: (String) -> Boolean,
    ): List<LogMessage> {
        val process = ProcessBuilder(
            Paths.get(System.getProperty("java.home"), "bin", "java").pathString,
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=info",
            "-Dorg.slf4j.simpleLogger.showDateTime=false",
            "-Dorg.slf4j.simpleLogger.showThreadName=false",
            "-cp", System.getProperty("java.class.path"),
            checkNotNull(kClass.qualifiedName) { "$kClass has no qualified name" },
            *arguments,
        ).also {
            it.directory(workingDirectory.toFile())
            it.customize()
            it.redirectErrorStream(true)
        }.start()

        val lines = Collections.synchronizedList(mutableListOf<String>())
        val outputConsumer = thread {
            process.inputStream.bufferedReader().forEachLine {
                println(it)
                lines.add(it)
                if (predicate(it)) process.toHandle().destroy()
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
        outputConsumer.join(5.seconds.inWholeMilliseconds)

        return lines.mapNotNull(LogMessage::parse)
    }

    @AfterTest
    fun tearDownWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }
}


data class LogMessage(val level: Level, val message: String) {
    enum class Level { TRACE, DEBUG, INFO, WARN, ERROR }

    companion object {
        private val LINE = Regex("""^\[(?<level>TRACE|DEBUG|INFO|WARN|ERROR)] (?<logger>\S+) - (?<message>.*)$""")

        /** Returns the message of a simple-logger line, or `null` for a continuation line. */
        fun parse(line: String): LogMessage? = LINE.matchEntire(line)?.let {
            LogMessage(Level.valueOf(it.groups["level"]!!.value), it.groups["message"]!!.value)
        }
    }
}
