package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.IP
import com.bkahlert.netmon.net.InterfaceFilter
import com.bkahlert.netmon.net.cidr
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class NmapNetworkScannerTest {

    @Test
    fun scan() {
        val cidr = InterfaceFilter.filter().values.first().first().cidr
        NmapNetworkScanner().scan(cidr).shouldNotBeEmpty()
    }

    @Test
    fun resolve() {
        val cidr = InterfaceFilter.filter().values.first().first().cidr
        NmapNetworkScanner().resolve(IP("192.168.16.1")) shouldBe "FRITZ"
        NmapNetworkScanner().resolve(IP("8.8.8.8")).shouldBeNull()
    }
}
