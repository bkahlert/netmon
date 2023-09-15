package com.bkahlert.netmon

import com.bkahlert.kommons.config.Settings
import com.bkahlert.kommons.config.setting
import com.bkahlert.kommons.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.kommons.text.Template
import com.bkahlert.netmon.serialization.JsonFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object ScanEventSettings : Settings("scan") {

    /** The topic name for scan events. */
    val topic: Template by setting(default = Template("dt/netmon/\${node}/\${interface}/\${cidr}/scan"), stringFormat = JsonFormat.unquoted)

    /** The duration after which scans are considered dated / lacking behind. */
    val datedThreshold: Duration by setting(default = 2.minutes)

    /** The duration after which scans are no longer displayed. */
    val outdatedThreshold: Duration by setting(default = 5.minutes)
}
