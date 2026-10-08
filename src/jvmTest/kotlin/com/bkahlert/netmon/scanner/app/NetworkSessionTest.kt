package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.contract.Cidr
import com.bkahlert.netmon.scanner.scan.NetmonScanner
import com.bkahlert.netmon.scanner.scan.NetworkContext
import com.bkahlert.netmon.scanner.scan.NetworkScan
import com.bkahlert.netmon.scanner.scan.ScanMode
import com.bkahlert.netmon.scanner.scan.ScanResult
import com.bkahlert.netmon.scanner.state.ScanStateStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlin.test.Test

class NetworkSessionTest {

    @Test
    fun scan_delegates_to_its_scanner() {
        var scans = 0
        val session = NetworkSession.open { testScanner { scans++ } }

        session.scan()

        scans shouldBe 1
    }

    @Test
    fun close_releases_owned_resources_once() {
        val order = mutableListOf<String>()
        val session = NetworkSession.open { resources ->
            resources.own(AutoCloseable { order += "resource" })
            testScanner()
        }

        session.close()
        session.close()

        order shouldContainExactly listOf("resource")
    }

    @Test
    fun scanner_creation_failure_remains_primary_when_cleanup_also_fails() {
        val order = mutableListOf<String>()
        val startupFailure = IllegalStateException("startup failure")
        val cleanupFailure = IOException("cleanup failure")

        val failure = shouldThrow<IllegalStateException> {
            NetworkSession.open { resources ->
                resources.own(AutoCloseable { order += "first" })
                resources.own(AutoCloseable { order += "last"; throw cleanupFailure })
                throw startupFailure
            }
        }

        failure shouldBe startupFailure
        failure.suppressed.toList() shouldContainExactly listOf(cleanupFailure)
        order shouldContainExactly listOf("last", "first")
    }
}

internal fun testScanner(onNormalScan: () -> Unit = {}): NetmonScanner {
    val context = NetworkContext("eth0", Cidr.parse("10.0.0.0/24"))
    return NetmonScanner(
        context = context,
        scanner = NetworkScan { _, mode ->
            if (mode == ScanMode.NORMAL) onNormalScan()
            emptyList()
        },
        enrichers = emptyList(),
        state = object : ScanStateStore {
            override fun load(): ScanResult = ScanResult(
                `interface` = context.interfaceName,
                cidr = context.cidr,
                timestamp = kotlin.time.Instant.fromEpochSeconds(0),
                hosts = emptyList(),
            )

            override fun save(scan: ScanResult) = Unit
        },
        clock = object : kotlin.time.Clock {
            override fun now(): kotlin.time.Instant = kotlin.time.Instant.fromEpochSeconds(0)
        },
        downAfter = kotlin.time.Duration.ZERO,
        onScan = {},
        onChange = {},
    )
}
