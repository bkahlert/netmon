package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.net.InterfaceFilter
import com.bkahlert.netmon.net.cidr
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.Test

class NmapNetworkScannerTest {

    @Test
    fun scan() {
        val cidr = InterfaceFilter.filter().values.first().first().cidr
        NmapNetworkScanner().scan(cidr).shouldNotBeEmpty()
    }
}
