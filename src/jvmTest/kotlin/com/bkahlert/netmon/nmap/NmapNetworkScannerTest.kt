package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.test.createTempDirectory
import com.bkahlert.netmon.logging.LoggingSettings
import com.bkahlert.netmon.net.SystemInterfaceAddressResolver
import com.bkahlert.netmon.net.cidr
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createFile
import kotlin.test.Test

class NmapNetworkScannerTest {

    val cidr = SystemInterfaceAddressResolver().resolve().first().cidr

    @Test
    fun scan() = runTest {
        NmapNetworkScanner().scan(cidr).shouldNotBeEmpty()
    }

    @Test
    fun scan_with_empty_mac_prefixes() = runTest {
        val dataDir = createTempDirectory("nmap-data").apply {
            resolve("nmap-mac-prefixes").createFile()
        }
        val scanner = NmapNetworkScanner(dataDir = dataDir)
        scanner.scan(cidr).forAll {
            it.vendor.shouldBeNull()
        }
    }

    @Test
    fun scan_with_updated_mac_prefixes() = runTest {
        LoggingSettings.apply("-vvv")
        val dataDir = createTempDirectory("nmap-data").also {
            NmapMacPrefixesProvisioner(FileCache.of("netmon-test")).provisionIn(it)
        }
        val scanner = NmapNetworkScanner(dataDir = dataDir)
        scanner.scan(cidr).forAtLeastOne {
            it.vendor.shouldNotBeNull()
        }
    }
}
