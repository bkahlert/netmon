package com.bkahlert.netmon

import com.bkahlert.kommons.config.Settings
import com.bkahlert.kommons.config.setting
import com.bkahlert.kommons.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.kommons.text.Template
import com.bkahlert.netmon.serialization.JsonFormat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

object HostEventSettings : Settings("host") {

    /** The topic name for host events. */
    val topic: Template by setting(default = Template("dt/netmon/\${node}/\${interface}/\${cidr}/host"), stringFormat = JsonFormat.unquoted)

    /** The duration after which a host state is considered stable. */
    val stabilizedThreshold: Duration by setting(default = 1.hours)
}
