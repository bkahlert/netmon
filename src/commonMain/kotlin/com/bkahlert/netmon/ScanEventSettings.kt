package com.bkahlert.netmon

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object ScanEventSettings : Settings("scan") {

    /** The topic name for scan events. */
    val topic: String by setting(default = "dt/netmon/\${node}/\${interface}/\${cidr}/scan")

    /** The duration after which scans are no longer displayed. */
    val outdatedThreshold: Duration by setting(default = 5.minutes)
}
