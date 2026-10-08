package com.bkahlert.netmon.scanner.state

import com.bkahlert.netmon.scanner.scan.ScanResult

interface ScanStateStore {
    fun load(): ScanResult?

    fun save(scan: ScanResult)
}
