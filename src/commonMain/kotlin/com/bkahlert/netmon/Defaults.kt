package com.bkahlert.netmon

data object Defaults {
    val networks: List<Cidr> = listOf(Cidr("192.168.16.0/24"))
    val privileged: Boolean = true
}
