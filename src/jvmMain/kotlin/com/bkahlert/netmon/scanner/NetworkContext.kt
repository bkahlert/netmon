package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Cidr

data class NetworkContext(
    val interfaceName: String,
    val cidr: Cidr,
)
