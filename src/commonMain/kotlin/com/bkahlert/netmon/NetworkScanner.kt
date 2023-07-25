package com.bkahlert.netmon

fun interface NetworkScanner {
    fun scan(network: Cidr): ScanResult
}
