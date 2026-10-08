package com.bkahlert.netmon.display.app

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.contract.serialization.JsonFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Settings for the network monitor's web UI. */
object UiSettings : Settings("ui", JsonFormat.unquoted) {

    /** The interval in which the time-relevant information in the UI are updated. */
    val REFRESH_INTERVAL: Duration = 1.seconds

    /** How long the radar icons animate after a scan arrives. */
    val SCAN_PULSE_DURATION: Duration = 10.seconds
}
