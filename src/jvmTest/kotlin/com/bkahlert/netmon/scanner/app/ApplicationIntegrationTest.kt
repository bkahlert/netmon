package com.bkahlert.netmon.scanner.app

import kotlin.time.Clock
import com.bkahlert.netmon.scanner.scan.ScanResult
import com.bkahlert.netmon.scanner.support.test.LogMessage
import com.bkahlert.netmon.contract.serialization.JsonFormat
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAny
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.should
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.test.runTest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import com.bkahlert.netmon.scanner.support.test.AbstractIntegrationTest

class ApplicationIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun scan_and_publish() = runTest(timeout = 150.seconds) {
        val logMessages = runUntilLogged(
            kClass = ApplicationIntegrationTest::class,
            "-v",
            customize = {
                environment()["BROKER_HOST"] = mqttContainer.host
                environment()["BROKER_PORT"] = mqttContainer.firstMappedPort.toString()
                environment()["DEBUG"] = "*.netmon*,-*mdns*"
            }
        ) { workingDirectory.listDirectoryEntries("scan.*.json").isNotEmpty() }

        logMessages should {
            it.shouldNotBeEmpty()
            it.forAll { (level, _) -> level shouldNotBe LogMessage.Level.ERROR }
            it.forAll { (level, _) -> level shouldNotBe LogMessage.Level.WARN }
            it.forAny { (_, message) -> message.shouldContain("Configuration: ") }
            it.forAny { (_, message) -> message.shouldContain("Settings: ") }
            it.forAny { (_, message) -> message.shouldContain("Provisioned file=nmap-mac-prefixes at path=./nmap/nmap-mac-prefixes") }
            it.forAny { (_, message) -> message.shouldContain("host(s) completed and published") }
            it.forAny { (_, message) -> message.shouldContain("Stopped scanning") }
            it.last().message shouldMatch Regex("Terminated SlicedApplication\\(state=Terminated, .*, failed=\\[]\\)")
        }
        workingDirectory should {
            it.resolve("nmap/nmap-mac-prefixes") should { mappingFile ->
                mappingFile.shouldExist()
                mappingFile.fileSize() shouldBeGreaterThan 500_000L
            }

            it.listDirectoryEntries("scan.*.json").forAll { scanResultFile ->
                scanResultFile.fileSize() shouldBeGreaterThan 0L
                JsonFormat.decodeFromString<ScanResult>(scanResultFile.readText()) should { scanResult ->
                    scanResult.timestamp.shouldBeGreaterThan(Clock.System.now() - 30.seconds)
                }
            }
        }
    }

    private val mqttContainer: GenericContainer<*> = GenericContainer<Nothing>(DockerImageName.parse("eclipse-mosquitto:1.5")).withExposedPorts(1883)

    @BeforeTest
    fun setUp() {
        mqttContainer.start()
    }

    @AfterTest
    fun tearDown() {
        mqttContainer.stop()
    }

    companion object {
        @JvmStatic
        fun main(vararg args: String) = Application.main(args)
    }
}
