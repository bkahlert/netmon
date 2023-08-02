package com.bkahlert.netmon

import com.bkahlert.netmon.net.InterfaceFilter
import io.kotest.assertions.asClue
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds

class LazyNameResolverTest {

    val localHostname: String = InetAddress.getLocalHost().hostName
    val localIpAddresses: List<IP> = InterfaceFilter.filter()
        .values
        .flatten()
        .map { IP(it.address) }

    @Test
    fun resolve() = runTest {
        val resolver = LazyNameResolver()
        val names: MutableMap<IP, String?> = localIpAddresses.associateWith { null }.toMutableMap()
        while (names.values.any { it == null }) {
            names.keys.forEach { ip ->
                names[ip] = resolver.resolve(ip)
            }
            delay(100.milliseconds)
        }
        names.forAll { (ip, name) ->
            ip.asClue { name shouldBe localHostname }
        }
    }
}
