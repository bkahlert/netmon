package com.bkahlert.netmon.scanner.scan

import com.bkahlert.netmon.contract.Cidr

data class NetworkContext(
    val interfaceName: String,
    val cidr: Cidr,
)
