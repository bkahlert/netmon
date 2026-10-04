package com.bkahlert.netmon

import com.bkahlert.netmon.otlp.MetricsData
import com.bkahlert.netmon.otlp.ResourceMetrics
import com.bkahlert.netmon.serialization.JsonFormat
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A sample of the kiosk from a `netmon-metrics` message: taken [at] the epoch second by a sampler that publishes every
 * [interval] seconds; [kioskCpu] and [webCpu] in percent of one core, [kioskMemory] the unit's RAM plus swap usage in
 * bytes, each `null` when its source was absent.
 */
data class KioskStats(
    val at: Long,
    val interval: Int,
    val kioskCpu: Int? = null,
    val webCpu: Int? = null,
    val kioskMemory: Long? = null,
) {
    /** Returns whether the sample is less than three intervals away from [now], behind it or, on a viewer whose clock lags the kiosk's, ahead of it. */
    fun isFreshAt(now: Instant): Boolean = (now - Instant.fromEpochSeconds(at)).absoluteValue < interval.seconds * 3

    companion object {
        /** The sampler's interval, at which the page checks the sample's freshness again. */
        val INTERVAL: Duration = 5.seconds
    }
}

/** Returns [percent] as the panel shows a CPU share, `114 %`. */
fun cpuText(percent: Int): String = "$percent %"

/** Returns [bytes] as the panel shows memory, whole mebibytes labelled MB as the soak tables do, `161 MB`. */
fun memoryText(bytes: Long): String = "${bytes / 1_048_576} MB"

/** The topic `netmon-metrics` publishes on, for every node. */
const val METRICS_TOPIC: String = "dt/netmon/+/metrics"

private const val KIOSK_UNIT = "pihero-kiosk.service"
private const val WEB_PROCESS = "WPEWebProcess"

/**
 * Returns the kiosk's figures in an OTLP/JSON [payload] of `netmon-metrics`, CPU as a share of one core, or `null` for
 * an empty payload, one that does not decode, or one without a data point.
 */
fun kioskStatsOf(payload: ByteArray): KioskStats? {
    if (payload.isEmpty()) return null
    val request = runCatching { JsonFormat.decodeFromString<MetricsData>(payload.decodeToString()) }
        .onFailure { com.bkahlert.kommons.js.console.error("Failed to decode the metrics", it) }
        .getOrNull() ?: return null
    val at = request.resourceMetrics
        .flatMap { resource -> resource.scopeMetrics.flatMap { it.metrics }.flatMap { it.dataPoints } }
        .maxOfOrNull { it.timeUnixNano.toLong() } ?: return null
    val host = request.resourceMetrics.firstOrNull { it.resource["service.name"] == "netmon-metrics" }
    val kiosk = request.resourceMetrics.firstOrNull { it.resource["systemd.unit.name"] == KIOSK_UNIT && it.resource["process.pid"] == null }
    val web = request.resourceMetrics.firstOrNull { it.resource["process.executable.name"] == WEB_PROCESS }
    val cores = host?.metric("system.cpu.logical.count")?.dataPoints?.firstOrNull()?.value
    fun ResourceMetrics.share(name: String): Int? =
        cores?.let { cores -> metric(name)?.dataPoints?.firstOrNull()?.value?.let { (it * cores * 100).roundToInt() } }
    fun ResourceMetrics.usage(type: String): Long? = metric("systemd.unit.memory.usage")?.dataPoints?.firstOrNull { it["type"] == type }?.value?.toLong()
    val ram = kiosk?.usage("ram")
    return KioskStats(
        at = at / 1_000_000_000,
        interval = INTERVAL_SECONDS,
        kioskCpu = kiosk?.share("systemd.unit.cpu.utilization"),
        webCpu = web?.share("process.cpu.utilization"),
        kioskMemory = ram?.let { it + (kiosk.usage("swap") ?: 0) },
    )
}

private val INTERVAL_SECONDS: Int = KioskStats.INTERVAL.inWholeSeconds.toInt()
