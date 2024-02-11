package com.bkahlert.netmon.logging

import ch.qos.logback.classic.Level
import com.bkahlert.kommons.logging.logback.Logback
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LoggingSettingsTest {

    @Test
    fun apply() = runTest {
        System.setProperty("debug", "*.netmon*,-*mdns*")
        LoggingSettings.apply("-v")
        forAll(
            // enabled by debug
            row("com.bkahlert.netmon.net", Level.DEBUG),

            // disabled by debug
            row("javax.jmdns.impl.DNSIncoming", Level.OFF),
            row("com.bkahlert.netmon.mdns.JmDNSServiceInfoCache", Level.OFF),

            // unaffected by debug
            row("io.netty", Verbosity.VERY_VERBOSE.levels["io.netty"]),
        ) { logger, level ->
            Logback[logger].level shouldBe level
        }
    }
}
