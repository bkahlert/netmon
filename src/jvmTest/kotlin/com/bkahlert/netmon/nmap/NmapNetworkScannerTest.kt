package com.bkahlert.netmon.nmap

import ch.qos.logback.classic.Level
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.logging.get
import com.bkahlert.netmon.net.InterfaceFilter
import com.bkahlert.netmon.net.cidr
import com.bkahlert.netmon.net.ipRange
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NmapNetworkScannerTest {

    @Test
    fun scan() = runTest {
        val cidr = InterfaceFilter.filter().values.first().first().cidr
        NmapNetworkScanner().scan(cidr).shouldNotBeEmpty()
    }

    @Test
    fun resolve_success() = runTest {
        Logback["com.bkahlert.kommons.exec"].level = Level.WARN
        val scanner = NmapNetworkScanner(privileged = false)
        val ipRange = InterfaceFilter.filter().values.first().first().ipRange
        val resolvedName: String? = ipRange.firstNotNullOfOrNull { ip ->
            scanner.resolve(ip)
        }
        resolvedName.shouldNotBeNull()
    }

    @Test
    fun resolve_failure() = runTest {
        Logback["com.bkahlert.kommons.exec"].level = Level.WARN
        val scanner = NmapNetworkScanner(privileged = false)
        scanner.resolve(IP("8.8.8.8")).shouldBeNull()
    }
}
