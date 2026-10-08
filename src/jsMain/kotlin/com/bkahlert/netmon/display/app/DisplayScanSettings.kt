package com.bkahlert.netmon.display.app

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.support.text.Template
import com.bkahlert.netmon.contract.serialization.JsonFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object DisplayScanSettings : Settings("scan", JsonFormat.unquoted) {

    /** The topic template the display subscribes to for scan events. */
    val topic: Template by setting(default = Template("dt/netmon/\${node}/\${interface}/\${cidr}/scan"))

    /** The duration after which scans are considered dated / lacking behind. */
    val datedThreshold: Duration by setting(default = 2.minutes)

    /** The duration after which scans are no longer displayed. */
    val outdatedThreshold: Duration by setting(default = 5.minutes)
}
