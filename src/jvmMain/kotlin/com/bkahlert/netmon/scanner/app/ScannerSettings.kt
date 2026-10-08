package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.contract.serialization.JsonFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Settings for the [NmapNetworkScanner]. */
object ScannerSettings : Settings("scanner", JsonFormat.unquoted) {

    val pauseDuration: Duration by setting(default = 30.seconds)

    /** The duration a host may stay unseen before it is reported DOWN. */
    val downAfter: Duration by setting(default = 3.minutes)
}
