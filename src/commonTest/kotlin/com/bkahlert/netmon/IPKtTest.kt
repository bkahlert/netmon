package com.bkahlert.netmon

import io.kotest.matchers.shouldBe
import org.junit.Test

class IPKtTest {

    @Test
    fun value() {
        val ip = "192.168.1.1"
        val value = ipv4ToInt(ip)
        value shouldBe 3232235777u
    }
}
