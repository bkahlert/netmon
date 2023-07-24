package com.bkahlert.netmon

fun ipv4ToInt(ip: String): UInt = ip.split(".")
    .map { it.toUInt() }
    .reduce { acc, value -> acc.shl(8) or value }
