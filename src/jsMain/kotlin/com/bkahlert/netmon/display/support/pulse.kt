package com.bkahlert.netmon.display.support

import com.bkahlert.netmon.display.app.DisplayScanSettings
import com.bkahlert.netmon.display.app.UiSettings
import kotlin.time.Duration

/**
 * Returns the class of the radar icons for a scan published [sincePublished] ago: the animation while younger than
 * [pulseDuration], the dated colour once older than [datedThreshold], no class in between.
 */
fun radarClass(
    sincePublished: Duration,
    pulseDuration: Duration = UiSettings.SCAN_PULSE_DURATION,
    datedThreshold: Duration = DisplayScanSettings.datedThreshold,
): String = when {
    sincePublished > datedThreshold -> "text-yellow-500/60"
    sincePublished < pulseDuration -> "animate-variable-color"
    else -> ""
}
