package com.bkahlert.netmon

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CidrTest {

    @Test
    fun to_string() {
        Cidr("10.0.0.0/16").toString() shouldBe "10.0.0.0/16"
    }
}
