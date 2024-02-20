package com.bkahlert.netmon.scanner

import com.bkahlert.kommons.config.Settings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Settings for the [NmapNetworkScanner]. */
object ScannerSettings : Settings("scanner") {

    val pauseDuration: Duration by setting(default = 10.seconds)
}
