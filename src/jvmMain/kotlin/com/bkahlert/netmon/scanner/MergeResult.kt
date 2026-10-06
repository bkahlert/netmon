package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Host

data class MergeResult(
    val scan: ScanResult,
    val changedHosts: List<Host>,
)
