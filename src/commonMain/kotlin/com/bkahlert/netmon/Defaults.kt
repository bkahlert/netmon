package com.bkahlert.netmon

import com.bkahlert.io.File

data object Defaults {
    val networks: List<Cidr> = listOf(Cidr("192.168.16.0/24"))
    val privileged: Boolean = true
    val resultFile: File = File(".netmon-result.json")
}
