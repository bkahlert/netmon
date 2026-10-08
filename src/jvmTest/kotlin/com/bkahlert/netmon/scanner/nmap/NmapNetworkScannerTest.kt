package com.bkahlert.netmon.scanner.nmap

import com.bkahlert.netmon.scanner.support.cache.FileCache
import com.bkahlert.netmon.scanner.support.logging.LoggingSettings
import com.bkahlert.netmon.scanner.support.net.SystemInterfaceAddressResolver
import com.bkahlert.netmon.scanner.support.net.cidr
import com.bkahlert.netmon.support.test.createTempDirectory
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createFile
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText
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
        assumeTrue(System.getProperty("user.name") == "root", "nmap reads MAC addresses, and so vendors, only with raw socket privileges")
        val scanner = NmapNetworkScanner(dataDir = dataDir)
        scanner.scan(cidr).forAtLeastOne {
            it.vendor.shouldNotBeNull()
        }
    }

    @Test
    fun propagates_an_interruption_while_waiting_for_nmap() = runTest {
        val scanner = NmapNetworkScanner(privileged = false, dataDir = null, binary = nmapThatClosesItsOutputAndKeepsRunning())

        Thread.currentThread().interrupt()
        val result = runCatching { scanner.scan(cidr) }

        result.exceptionOrNull().shouldBeInstanceOf<InterruptedException>()
    }

    private fun TestScope.nmapThatClosesItsOutputAndKeepsRunning(): Path =
        createTempDirectory("nmap-bin").resolve("nmap").apply {
            writeText("#!/bin/sh\nexec >&-\nsleep 2\n")
            setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
        }
}
