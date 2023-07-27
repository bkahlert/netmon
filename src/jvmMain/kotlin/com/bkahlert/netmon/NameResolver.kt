package com.bkahlert.netmon

fun interface NameResolver {
    fun resolve(ip: IP): String?
}
