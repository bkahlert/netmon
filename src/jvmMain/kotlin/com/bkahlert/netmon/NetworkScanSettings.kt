package com.bkahlert.netmon

/** Settings for the network monitor's scanner. */
object NetworkScanSettings : Settings("scan") {

    /** Whether the host scanning process runs privileged. */
    val privileged: Boolean by setting(default = true)
}
