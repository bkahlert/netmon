package com.bkahlert.netmon.net

import java.net.InterfaceAddress

fun interface InterfaceAddressResolver {
    fun resolve(): List<InterfaceAddress>
}
