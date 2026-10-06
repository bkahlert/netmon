package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host

enum class ScanMode {
    INITIAL,
    NORMAL,
}

fun interface NetworkScan {
    fun scan(network: Cidr, mode: ScanMode): List<Host>
}
