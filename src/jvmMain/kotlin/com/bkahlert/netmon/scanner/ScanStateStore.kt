package com.bkahlert.netmon.scanner

interface ScanStateStore {
    fun load(): ScanResult?

    fun save(scan: ScanResult)
}
