package com.bkahlert.netmon

import com.bkahlert.kommons.exec.environment
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAny
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.should
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import kotlin.io.path.fileSize
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration.Companion.seconds

class ApplicationIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun scan_and_publish() = runTest(timeout = 30.seconds) {
        val logMessages = runUntilLogged(
            kClass = ApplicationIntegrationTest::class,
            "-v",
            customize = {
                environment["BROKER_HOST"] = mqttContainer.host
                environment["BROKER_PORT"] = mqttContainer.firstMappedPort.toString()
                environment["DEBUG"] = "*.netmon*,-*mdns*"
            }
        ) { it.contains("host(s) completed and published") }

        logMessages should {
            it.shouldNotBeEmpty()
            it.forAll { (level, _) -> level shouldNotBe LogMessage.Level.ERROR }
            it.forAll { (level, _) -> level shouldNotBe LogMessage.Level.WARN }
            it.forAny { (_, message) -> message.shouldContain("Settings: ") }
            it.forAny { (_, message) -> message.shouldContain("Provisioned file=nmap-mac-prefixes at path=./nmap/nmap-mac-prefixes") }
            it.forAny { (_, message) -> message.shouldContain("host(s) completed and published") }
            it.forAny { (_, message) -> message.shouldContain("Stopped scanning") }
            it.last().message shouldMatch Regex("Terminated SlicedApplication\\(state=Terminated, .*, failed=\\[]\\)")
        }
        workingDirectory.resolve("nmap/nmap-mac-prefixes") should {
            it.shouldExist()
            it.fileSize() shouldBeGreaterThan 500_000L
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
