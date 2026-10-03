package com.bkahlert.netmon

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A sample of the kiosk written by `netmon-display-stats`: taken [at] the epoch second, [interval] seconds after the one
 * before; [kioskCpu] and [webCpu] in percent of one core, [kioskMemory] RAM plus zram in bytes, each `null` when its
 * source was absent.
 */
@Serializable
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
        /** The sampler's interval, which the page polls at as well. */
        val INTERVAL: Duration = 5.seconds
    }
}

/** Returns [percent] as the panel shows a CPU share, `114 %`. */
fun cpuText(percent: Int): String = "$percent %"

/** Returns [bytes] as the panel shows memory, whole mebibytes labelled MB as the soak tables do, `161 MB`. */
fun memoryText(bytes: Long): String = "${bytes / 1_048_576} MB"
