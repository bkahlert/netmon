package com.bkahlert.netmon.scanner.support.net

import java.net.InterfaceAddress

fun interface InterfaceAddressResolver {
    fun resolve(): List<InterfaceAddress>
}
