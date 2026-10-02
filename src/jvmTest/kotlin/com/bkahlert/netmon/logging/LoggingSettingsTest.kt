package com.bkahlert.netmon.logging

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LoggingSettingsTest {

    @Test
    fun apply_sets_the_simple_loggers_levels_from_verbosity_and_debug() = runTest {
        System.setProperty("debug", "*.netmon*,-*mdns*")

        LoggingSettings.apply("-v")

        forAll(
            row("com.bkahlert.netmon.net", LogLevel.DEBUG),
            row("javax.jmdns.impl.DNSIncoming", LogLevel.OFF),
            row("com.bkahlert.netmon.mdns.JmDNSServiceInfoCache", LogLevel.OFF),
            row(SimpleLogger.ROOT, LogLevel.INFO),
        ) { logger, level ->
            SimpleLogger.level(logger) shouldBe level
        }
    }
}
