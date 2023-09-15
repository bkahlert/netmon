package com.bkahlert.netmon

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.exec.environment
import com.bkahlert.kommons.exec.workingDirectory
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldNotContainIgnoringCase
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.io.path.fileSize
import kotlin.test.Test
import main as mainMain

class IntegrationTest {

    @Test
    fun start() {
        val output: MutableList<String> = mutableListOf()
        val dir = createTempDirectory("netmon-integration-test")
        val app = CommandLine(IntegrationTest::class, "-vvv").let(::ProcessBuilder).apply {
            workingDirectory = dir
            environment["BROKER_HOST"] = "foo.local"
            environment["DEBUG"] = "*.netmon*,-*mdns*"
//            redirectOutput(outputFile.toFile())
            redirectErrorStream(true)
        }.start().apply {
            thread {
                inputStream.bufferedReader().forEachLine {
                    output.add(it)
                    println(it)
                }
            }
        }
        Thread.sleep(5000)
        app.destroy()

        output should {
            it.shouldNotBeEmpty()
            it.forAll { line -> line.shouldNotContainIgnoringCase("error") }
        }
        dir.resolve("nmap/nmap-mac-prefixes") should {
            it.shouldExist()
            it.fileSize() shouldBeGreaterThan 500_000L
        }
    }

    companion object {
        @JvmStatic
        fun main(vararg args: String) = mainMain(args)
    }
}
