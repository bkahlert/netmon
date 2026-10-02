package com.bkahlert.netmon

import com.bkahlert.kommons.config.Settings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Settings for the network monitor's web UI. */
object UiSettings : Settings("ui") {

    /** The interval in which the time-relevant information in the UI are updated. */
    val REFRESH_INTERVAL: Duration = 1.seconds

    /** The duration for which changes are highlighted. */
    val HOST_STATE_CHANGE_HIGHLIGHT_DURATION: Duration = 60.seconds

    /** How long the radar icons animate after a scan arrives. */
    val SCAN_PULSE_DURATION: Duration = 10.seconds
}
