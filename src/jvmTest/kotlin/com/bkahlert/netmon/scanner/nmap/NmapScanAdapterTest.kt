package com.bkahlert.netmon.scanner.nmap

import com.bkahlert.netmon.contract.Cidr
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.Status
import com.bkahlert.netmon.scanner.scan.NetworkScan
import com.bkahlert.netmon.scanner.scan.ScanMode
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NmapScanAdapterTest {

    @Test
    fun initial_scans_use_the_insane_timing_template() {
        val network = Cidr.parse("10.0.0.0/24")
        val hosts = listOf(Host(ip = IP.of("10.0.0.1"), status = Status.UP))
        val calls = mutableListOf<Pair<Cidr, TimingTemplate>>()
        val scanner: NetworkScan = NmapScanAdapter { cidr, timing ->
            calls += cidr to timing
            hosts
        }

        val result = scanner.scan(network, ScanMode.INITIAL)

        result shouldBe hosts
        calls shouldBe listOf(network to TimingTemplate.Insane)
    }

    @Test
    fun normal_scans_use_the_aggressive_timing_template() {
        val network = Cidr.parse("10.0.0.0/24")
        val hosts = listOf(Host(ip = IP.of("10.0.0.2"), status = Status.UP))
        val calls = mutableListOf<Pair<Cidr, TimingTemplate>>()
        val scanner: NetworkScan = NmapScanAdapter { cidr, timing ->
            calls += cidr to timing
            hosts
        }

        val result = scanner.scan(network, ScanMode.NORMAL)

        result shouldBe hosts
        calls shouldBe listOf(network to TimingTemplate.Aggressive)
    }
}
