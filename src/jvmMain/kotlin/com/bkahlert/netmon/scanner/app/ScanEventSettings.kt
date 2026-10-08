package com.bkahlert.netmon.scanner.app

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.support.text.Template
import com.bkahlert.netmon.contract.serialization.JsonFormat

object ScanEventSettings : Settings("scan", JsonFormat.unquoted) {

    /** The topic name for scan events. */
    val topic: Template by setting(default = Template("dt/netmon/\${node}/\${interface}/\${cidr}/scan"))
}
