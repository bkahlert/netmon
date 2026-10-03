package com.bkahlert.netmon

import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class KioskStatsTest {

    @Test
    fun decodes_the_samplers_json() {
        val stats = JsonFormat.decodeFromString<KioskStats>("""{"at":1759450000,"interval":5,"kioskCpu":118,"webCpu":114,"kioskMemory":168820736}""")

        stats shouldBe KioskStats(at = 1759450000, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
    }

    @Test
    fun decodes_absent_sources_as_null() {
        val stats = JsonFormat.decodeFromString<KioskStats>("""{"at":1759450000,"interval":5,"kioskCpu":null,"webCpu":null,"kioskMemory":null}""")

        stats shouldBe KioskStats(at = 1759450000, interval = 5)
    }

    @Test
    fun is_fresh_for_less_than_three_intervals() {
        val stats = KioskStats(at = 1_000, interval = 5)

        stats.isFreshAt(Instant.fromEpochSeconds(1_014)) shouldBe true
        stats.isFreshAt(Instant.fromEpochSeconds(1_015)) shouldBe false
    }

    @Test
    fun formats_cpu_and_memory_as_the_panel_shows_them() {
        cpuText(114) shouldBe "114 %"
        memoryText(168_820_736) shouldBe "161 MB"
    }
}
