package com.bkahlert.netmon.scanner.state

import com.bkahlert.netmon.scanner.support.test.AbstractIntegrationTest
import com.bkahlert.netmon.scanner.scan.ScanResult
import com.bkahlert.netmon.contract.Cidr
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed
import com.bkahlert.netmon.scanner.support.test.LogMessage
import com.bkahlert.netmon.contract.Status
import com.bkahlert.netmon.contract.epoch
import com.bkahlert.netmon.contract.serialization.JsonFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.file.Paths
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.Test

class JsonScanStateStoreTest : AbstractIntegrationTest() {

    @Test
    fun an_older_file_loads_with_optional_fields_absent() {
        val resource = checkNotNull(javaClass.classLoader.getResource("assets/older-scan.json"))

        val loaded = JsonScanStateStore(Paths.get(resource.toURI())).load()
        val host = checkNotNull(loaded).hosts.single()

        host.lastSeen shouldBe null
        host.kind shouldBe null
        host.link shouldBe null
        host.speed shouldBe null
    }

    @Test
    fun two_writes_replace_the_same_file() {
        val file = workingDirectory.resolve("scan.json").also { it.writeText("stale") }
        val store = JsonScanStateStore(file)
        val first = scanAt(100.epoch, Host(ip = IP.of("10.0.0.1"), status = Status.UP))
        val second = scanAt(200.epoch, Host(ip = IP.of("10.0.0.2"), status = Status.UP, kind = Kind.ROUTER, link = Link.WIFI, speed = LinkSpeed(866)))

        store.save(first)
        store.save(second)
        val loaded = store.load()
        val files = workingDirectory.listDirectoryEntries()

        loaded shouldBe second
        files.size shouldBe 1
        files.single() shouldBe file
    }

    @Test
    fun unreadable_json_logs_and_returns_null() {
        val file = workingDirectory.resolve("broken.json").also { it.writeText("{") }
        val store = JsonScanStateStore(file)

        val result = store.load()
        val logMessages = runUntilLogged(
            kClass = StateStoreLogProbe::class,
            "load",
            file.fileName.toString(),
            predicate = { it.contains("Error loading scan result") },
        )

        result shouldBe null
        logMessages.forAny { message ->
            message.level shouldBe LogMessage.Level.ERROR
            message.message shouldContain "Error loading scan result"
        }
    }

    @Test
    fun file_read_errors_propagate() {
        val directory = workingDirectory.resolve("scan.json").createDirectory()

        shouldThrow<IOException> { JsonScanStateStore(directory).load() }
    }

    @Test
    fun failed_saves_are_logged() {
        val file = workingDirectory.resolve("missing").resolve("scan.json")
        val store = JsonScanStateStore(file)

        store.save(scanAt(100.epoch))
        val logMessages = runUntilLogged(
            kClass = StateStoreLogProbe::class,
            "save",
            "missing/scan.json",
            predicate = { it.contains("Error saving scan result") },
        )

        file.exists() shouldBe false
        logMessages.forAny { message ->
            message.level shouldBe LogMessage.Level.ERROR
            message.message shouldContain "Error saving scan result"
        }
    }

    @Test
    fun interrupted_saves_keep_their_info_log() {
        val logMessages = runUntilLogged(
            kClass = StateStoreLogProbe::class,
            "interrupted-save",
            "scan.json",
            predicate = { it.contains("Aborted saving scan result") },
        )

        logMessages.forAny { message ->
            message.level shouldBe LogMessage.Level.INFO
            message.message shouldContain "Aborted saving scan result"
        }
    }
}

object StateStoreLogProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = Paths.get(args[1])
        when (args[0]) {
            "load" -> JsonScanStateStore(file).load()
            "save" -> JsonScanStateStore(file).save(scanAt(100.epoch))
            "interrupted-save" -> JsonScanStateStore(file, ClosedChannelStringFormat()).save(scanAt(100.epoch))
        }
    }
}

private class ClosedChannelStringFormat : StringFormat by JsonFormat {
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String =
        throw ClosedChannelException()
}

private fun scanAt(timestamp: kotlin.time.Instant, vararg hosts: Host) = ScanResult(
    `interface` = "en0",
    cidr = Cidr.parse("10.0.0.0/24"),
    timestamp = timestamp,
    hosts = hosts.toList(),
)
