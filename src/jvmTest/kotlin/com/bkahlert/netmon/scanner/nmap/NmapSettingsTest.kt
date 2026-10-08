package com.bkahlert.netmon.scanner.nmap

import com.bkahlert.netmon.scanner.support.cache.SystemLocations
import com.bkahlert.netmon.support.config.withTestConfig
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import java.nio.file.Paths
import kotlin.test.Test

class NmapSettingsTest {

    @Test
    fun data_dir_defaults_to_application_cache() = runTest {
        withTestConfig {
            val result = NmapSettings.dataDir

            result shouldBe SystemLocations.Cache.resolve("com.bkahlert.netmon/nmap")
        }
    }

    @Test
    fun privileged() = runTest {
        forAll(
            row("true", true),
            row("false", false),
        ) { value, expected ->
            withTestConfig("nmap.privileged" to value) {
                NmapSettings.privileged shouldBe expected
            }
        }
    }

    @Test
    fun data_dir() = runTest {
        forAll(
            row("./nmap", Paths.get("./nmap")),
            row("/tmp", Paths.get("/tmp")),
        ) { value, expected ->
            withTestConfig("nmap.dataDir" to value) {
                NmapSettings.dataDir shouldBe expected
            }
        }

        withTestConfig("nmap.dataDir" to "\u001B") {
            shouldThrowAny { NmapSettings.dataDir }
                .message?.lowercase().shouldContain("not writeable")
        }
    }
}
