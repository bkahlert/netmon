package com.bkahlert.netmon.scanner.support.logging

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class VerbosityTest {

    @Test
    fun from_integer() = runTest {
        forAll(
            row(0, Verbosity.ERRORS_AND_WARNINGS),
            row(1, Verbosity.VERBOSE),
            row(2, Verbosity.VERY_VERBOSE),
            row(3, Verbosity.EXTREMELY_VERBOSE),
            row(100, Verbosity.EXTREMELY_VERBOSE),
        ) { integer, expected ->
            Verbosity.from(integer) shouldBe expected
        }
    }

    @Test
    fun from_args() = runTest {
        forAll(
            row(emptyArray<String>(), Verbosity.ERRORS_AND_WARNINGS),
            row(arrayOf("-v"), Verbosity.VERBOSE),
            row(arrayOf("-vv"), Verbosity.VERY_VERBOSE),
            row(arrayOf("-v", "-v"), Verbosity.VERY_VERBOSE),
            row(arrayOf("-vvv"), Verbosity.EXTREMELY_VERBOSE),
            row(arrayOf("-v", "-vv"), Verbosity.EXTREMELY_VERBOSE),
            row(arrayOf("-vvvvvvvvvvvvvvv"), Verbosity.EXTREMELY_VERBOSE),
        ) { args, expected ->
            Verbosity.from(*args) shouldBe expected
        }
    }

    @Test
    fun level() = runTest {
        forAll(
            row(Verbosity.ERRORS_AND_WARNINGS, LogLevel.WARN),
            row(Verbosity.VERBOSE, LogLevel.INFO),
            row(Verbosity.VERY_VERBOSE, LogLevel.INFO),
            row(Verbosity.EXTREMELY_VERBOSE, LogLevel.DEBUG),
        ) { verbosity, expected ->
            SimpleLogger.configure(verbosity.levels)
            SimpleLogger.level(SimpleLogger.ROOT) shouldBe expected
        }
    }

    @Test
    fun package_logger_levels_follow_scanner_packages() {
        Verbosity.ERRORS_AND_WARNINGS.levels shouldBe mapOf(
            "root" to LogLevel.WARN,
            "javax.jmdns.impl.DNSIncoming" to LogLevel.ERROR,
            "com.bkahlert.netmon.scanner.app.Application" to LogLevel.INFO,
            "com.bkahlert.netmon.scanner.mqtt.ScannerEventPublisher" to LogLevel.INFO,
            "com.bkahlert.netmon.scanner.scan.enrichment" to LogLevel.ERROR,
        )
        Verbosity.VERBOSE.levels shouldBe mapOf(
            "root" to LogLevel.INFO,
            "javax.jmdns.impl.DNSIncoming" to LogLevel.ERROR,
            "com.bkahlert.netmon.scanner.support.exec" to LogLevel.WARN,
            "com.bkahlert.netmon.scanner.support.net" to LogLevel.WARN,
            "com.bkahlert.netmon.scanner.discovery.mdns.JmDNSServiceInfoCache" to LogLevel.WARN,
            "com.bkahlert.netmon.scanner.scan.enrichment" to LogLevel.WARN,
            "com.bkahlert.netmon.scanner.mqtt" to LogLevel.WARN,
        )
        Verbosity.VERY_VERBOSE.levels shouldBe mapOf(
            "root" to LogLevel.INFO,
            "javax.jmdns.impl.DNSIncoming" to LogLevel.ERROR,
            "com.bkahlert.netmon.scanner.app.Application" to LogLevel.DEBUG,
            "com.bkahlert.netmon.scanner.discovery.mdns" to LogLevel.DEBUG,
            "com.bkahlert.netmon.scanner.discovery.mdns.JmDNSServiceInfoCache" to LogLevel.INFO,
        )
    }
}
