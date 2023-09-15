package com.bkahlert.netmon.net

import com.bkahlert.netmon.IP
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class InterfaceFilterTest {

    @Test
    fun xxx() {
        InterfaceFilter.filter().values.first().first().cidr.ip shouldBe IP("192.168.16.33")
    }

}
