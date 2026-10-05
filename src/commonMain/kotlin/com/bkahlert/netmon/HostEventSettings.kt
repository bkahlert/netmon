package com.bkahlert.netmon

import com.bkahlert.kommons.config.Settings
import com.bkahlert.kommons.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.kommons.text.Template
import com.bkahlert.netmon.serialization.JsonFormat

object HostEventSettings : Settings("host") {

    /** The topic name for host events. */
    val topic: Template by setting(default = Template("dt/netmon/\${node}/\${interface}/\${cidr}/host"), stringFormat = JsonFormat.unquoted)
}
