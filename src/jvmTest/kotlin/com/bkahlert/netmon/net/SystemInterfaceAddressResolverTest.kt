package com.bkahlert.netmon.net

import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SystemInterfaceAddressResolverTest {

    @Test
    fun resolve() {
        val resolver = SystemInterfaceAddressResolver()
        resolver.resolve() should { interfaceAddresses ->
            interfaceAddresses.shouldNotBeEmpty()
            interfaceAddresses.forAtLeastOne {
                it.ipRange.asSequence().forAtLeastOne { ip ->
                    ip.addr.isReachable(1) shouldBe true
                }
            }
        }
    }

}
