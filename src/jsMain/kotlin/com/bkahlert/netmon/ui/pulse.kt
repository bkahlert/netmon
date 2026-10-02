package com.bkahlert.netmon.ui

import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.UiSettings
import kotlin.time.Duration

/**
 * Returns the class of the radar icons for a scan published [sincePublished] ago: the animation while younger than
 * [pulseDuration], the dated colour once older than [datedThreshold], no class in between.
 */
fun radarClass(
    sincePublished: Duration,
    pulseDuration: Duration = UiSettings.SCAN_PULSE_DURATION,
    datedThreshold: Duration = ScanEventSettings.datedThreshold,
): String = when {
    sincePublished > datedThreshold -> "text-yellow-500/60"
    sincePublished < pulseDuration -> "animate-variable-color"
    else -> ""
}
