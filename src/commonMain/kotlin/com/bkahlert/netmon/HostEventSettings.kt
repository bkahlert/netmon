package com.bkahlert.netmon

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

object HostEventSettings : Settings("host") {

    /** The topic name for host events. */
    val topic: String by setting(default = "dt/netmon/\${node}/\${interface}/\${cidr}/host")

    /** The duration after which a host state is considered stable. */
    val stabilizedThreshold: Duration by setting(default = 1.hours)
}
