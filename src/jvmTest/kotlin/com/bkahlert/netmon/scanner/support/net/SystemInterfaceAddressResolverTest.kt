package com.bkahlert.netmon.scanner.support.net

import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.net.InetAddress
import kotlin.test.Test

class SystemInterfaceAddressResolverTest {

    @Test
    fun resolve() {
        val resolver = SystemInterfaceAddressResolver(
            SystemInterfaceAddressResolver.NetworkInterfaceUpPredicate,
            SystemInterfaceAddressResolver.NetworkInterfaceNonLoopbackPredicate,
            SystemInterfaceAddressResolver.SiteOrLinkLocalIpAddressPredicate,
            SystemInterfaceAddressResolver.HostCountPredicate(0..128),
        )
        resolver.resolve() should { interfaceAddresses ->
            interfaceAddresses.shouldNotBeEmpty()
            interfaceAddresses.forAtLeastOne {
                it.ipRange.asSequence().forAtLeastOne { ip ->
                    InetAddress.getByAddress(ip.bytes).isReachable(1) shouldBe true
                }
            }
        }
    }

}
