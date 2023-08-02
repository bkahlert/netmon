package com.bkahlert.netmon.mdns

import com.bkahlert.netmon.IP
import com.bkahlert.netmon.net.InterfaceFilter
import io.kotest.assertions.asClue
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.net.InetAddress

class MulticastDnsReverseNameResolverTest {

    val localHostname: String = InetAddress.getLocalHost().hostName
    val localIpAddresses: List<IP> = InterfaceFilter.filter()
        .values
        .flatten()
        .map { IP(it.address) }

    @Test
    fun resolve_localhost() {
        localIpAddresses
            .shouldNotBeEmpty()
            .associateWith { MulticastDnsReverseNameResolver.resolve(it) }
            .forAll { (ip, name) ->
                ip.asClue { name shouldBe localHostname }
            }
    }

    @Test
    fun resolve_public() {
        MulticastDnsReverseNameResolver.resolve(IP("8.8.8.8")).shouldBeNull()
    }

    @Test
    fun resolve_invalid() {
        MulticastDnsReverseNameResolver.resolve(IP("invalid")).shouldBeNull()
    }
}
