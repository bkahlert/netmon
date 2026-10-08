package com.bkahlert.netmon.scanner.scan

import com.bkahlert.netmon.contract.Cidr
import com.bkahlert.netmon.contract.Host

enum class ScanMode {
    INITIAL,
    NORMAL,
}

fun interface NetworkScan {
    fun scan(network: Cidr, mode: ScanMode): List<Host>
}
