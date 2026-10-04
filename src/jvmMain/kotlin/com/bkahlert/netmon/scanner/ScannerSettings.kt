package com.bkahlert.netmon.scanner

import com.bkahlert.kommons.config.Settings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Settings for the [NmapNetworkScanner]. */
object ScannerSettings : Settings("scanner") {

    val pauseDuration: Duration by setting(default = 30.seconds)

    /** The duration a host may stay unseen before it is reported DOWN. */
    val downAfter: Duration by setting(default = 3.minutes)
}
