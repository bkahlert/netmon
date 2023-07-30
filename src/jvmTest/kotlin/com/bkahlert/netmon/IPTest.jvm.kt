package com.bkahlert.netmon

import io.kotest.matchers.shouldBe
import java.net.InetAddress
import kotlin.test.Test

class IPTestJvm {

    @Test
    fun instantiation() {
        val addr = InetAddress.getLocalHost()
        IP(addr) shouldBe IP(addr.hostAddress)
    }
}
