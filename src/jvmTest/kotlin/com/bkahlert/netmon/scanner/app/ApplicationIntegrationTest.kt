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
import java.io.File
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import com.bkahlert.netmon.scanner.support.test.AbstractIntegrationTest

class ApplicationIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun scan_and_publish() = runTest(timeout = 150.seconds) {
        val nmapBin = createFakeNmap()
        val logMessages = runUntilLogged(
            kClass = ApplicationIntegrationTest::class,
            "-v",
            customize = {
                environment()["BROKER_HOST"] = mqttContainer.host
                environment()["BROKER_PORT"] = mqttContainer.firstMappedPort.toString()
                environment()["DEBUG"] = "*.netmon*,-*mdns*"
                environment()["NMAP_PRIVILEGED"] = "false"
                environment()["NMAP_DATA_DIR"] = workingDirectory.resolve("nmap").toString()
                environment()["SCANNER_PAUSE_DURATION"] = "PT1S"
                environment()["PATH"] = "$nmapBin${File.pathSeparator}${environment()["PATH"].orEmpty()}"
            }
        ) { line ->
            line.contains("Executed Worker(") && workingDirectory.listDirectoryEntries("scan.*.json").isNotEmpty()
        }

        val scannedNetworks = logMessages
            .filter { logMessage -> logMessage.message.startsWith("Scanning network ") }
            .map { logMessage -> logMessage.message.removePrefix("Scanning network ") }
        scannedNetworks.shouldNotBeEmpty()
        scannedNetworks.forAll { it shouldMatch Regex("""::1(?:%\S+)?/128""") }

        logMessages should {
            it.shouldNotBeEmpty()
            it.forAll { (level, _) -> level shouldNotBe LogMessage.Level.ERROR }
            it.forAll { (level, message) ->
                if (level == LogMessage.Level.WARN) message.shouldContain("SSDP unavailable")
                else level shouldNotBe LogMessage.Level.ERROR
            }
            it.forAny { (_, message) -> message.shouldContain("Configuration: ") }
            it.forAny { (_, message) -> message.shouldContain("Settings: ") }
            it.forAny { (_, message) -> message shouldMatch Regex("""Interface addresses found: \S+:::1(?:%\S+)?/128""") }
            it.forAny { (_, message) ->
                message.shouldContain("Provisioned file=nmap-mac-prefixes at path=${workingDirectory.resolve("nmap/nmap-mac-prefixes")}")
            }
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

    private fun createFakeNmap(): Path = workingDirectory.resolve("bin").apply {
        createDirectories()
        resolve("nmap").apply {
            writeText(
                """
                #!/bin/sh
                cat <<'XML'
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE nmaprun>
                <nmaprun scanner="nmap" args="nmap -6 -sn -oX - ::1/128" start="0" version="7.94" xmloutputversion="1.05">
                  <host><status state="up" reason="user-set"/>
                    <address addr="::1" addrtype="ipv6"/>
                  </host>
                </nmaprun>
                XML
                """.trimIndent()
            )
            setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
        }
    }

    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            Application(
                hostname = "localhost",
                interfaceAddresses = { listOf(loopbackInterfaceAddress()) },
            ).start()
        }
    }
}

private fun loopbackInterfaceAddress(): InterfaceAddress {
    val loopback = InetAddress.getByName("::1")
    val networkInterface = checkNotNull(NetworkInterface.getByInetAddress(loopback)) {
        "IPv6 loopback interface not found"
    }
    return networkInterface.interfaceAddresses.single { it.address == loopback }
}
