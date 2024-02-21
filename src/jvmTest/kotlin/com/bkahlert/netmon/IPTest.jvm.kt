package com.bkahlert.netmon

import io.kotest.matchers.shouldBe
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.test.Test

class IPTestJvm {

    @Test
    fun instantiation() {
        val addr = InetAddress.getLocalHost()
        when (addr) {
            is Inet4Address -> IPv4(addr)
            is Inet6Address -> IPv6(addr)
            else -> throw AssertionError("Unknown address type: $addr")
        } shouldBe IP.of(addr.hostAddress)
    }
}
