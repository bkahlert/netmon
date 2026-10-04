package com.bkahlert.netmon

import com.bkahlert.netmon.fritz2.runTest
import com.bkahlert.netmon.model_identification.readBytes
import com.bkahlert.netmon.uri.toUri
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

@JsModule("./metrics.json")
@JsNonModule
private external val goldenMetrics: String

class KioskStatsTest {

    @Test
    fun reads_the_kiosks_figures_from_the_samplers_message() = runTest {
        val payload = goldenMetrics.toUri().readBytes()

        val stats = kioskStatsOf(payload)

        stats shouldBe KioskStats(at = 1759450005, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
    }

    @Test
    fun leaves_out_the_figures_of_absent_resources() {
        val stats = kioskStatsOf(HOST_ONLY.encodeToByteArray())

        stats shouldBe KioskStats(at = 1759450005, interval = 5)
    }

    @Test
    fun has_no_figures_in_an_empty_payload() {
        val stats = kioskStatsOf(ByteArray(0))

        stats shouldBe null
    }

    @Test
    fun has_no_figures_in_a_payload_that_is_not_a_request() {
        val stats = kioskStatsOf("<html lang=\"en\">".encodeToByteArray())

        stats shouldBe null
    }

    @Test
    fun has_no_figures_in_a_request_without_a_point() {
        val stats = kioskStatsOf("""{"resourceMetrics":[]}""".encodeToByteArray())

        stats shouldBe null
    }

    @Test
    fun has_no_figures_in_a_request_whose_point_time_is_not_a_number() {
        val stats = kioskStatsOf(pointAt("x"))

        stats shouldBe null
    }

    @Test
    fun has_no_figures_in_a_request_whose_point_time_exceeds_a_long() {
        val stats = kioskStatsOf(pointAt("9223372036854775808"))

        stats shouldBe null
    }

    @Test
    fun is_fresh_for_less_than_three_intervals() {
        val stats = KioskStats(at = 1_000, interval = 5)

        stats.isFreshAt(Instant.fromEpochSeconds(1_014)) shouldBe true
        stats.isFreshAt(Instant.fromEpochSeconds(1_015)) shouldBe false
    }

    @Test
    fun is_stale_when_dated_three_intervals_ahead_of_the_clock() {
        val stats = KioskStats(at = 1_015, interval = 5)

        stats.isFreshAt(Instant.fromEpochSeconds(1_001)) shouldBe true
        stats.isFreshAt(Instant.fromEpochSeconds(1_000)) shouldBe false
    }

    @Test
    fun formats_cpu_and_memory_as_the_panel_shows_them() {
        cpuText(114) shouldBe "114 %"
        memoryText(168_820_736) shouldBe "161 MB"
    }
}

private const val HOST_ONLY = """{"resourceMetrics":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"netmon-metrics"}}]},
"scopeMetrics":[{"metrics":[{"name":"system.cpu.logical.count","sum":{"aggregationTemporality":2,"dataPoints":[{"timeUnixNano":"1759450005000000000","asInt":"4"}]}}]}]}]}"""

private fun pointAt(timeUnixNano: String): ByteArray =
    """{"resourceMetrics":[{"scopeMetrics":[{"metrics":[{"name":"x","gauge":{"dataPoints":[{"timeUnixNano":"$timeUnixNano"}]}}]}]}]}""".encodeToByteArray()
