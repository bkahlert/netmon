package com.bkahlert.netmon.scanner.scan

import com.bkahlert.netmon.contract.Host

data class MergeResult(
    val scan: ScanResult,
    val changedHosts: List<Host>,
)
