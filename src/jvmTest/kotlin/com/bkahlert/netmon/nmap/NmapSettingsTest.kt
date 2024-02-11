package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.config.withTestConfig
import com.bkahlert.kommons.text.Unicode
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

        withTestConfig("nmap.dataDir" to "${Unicode.ESCAPE}") {
            shouldThrowAny { NmapSettings.dataDir }
                .message?.lowercase().shouldContain("not writeable")
        }
    }
}
