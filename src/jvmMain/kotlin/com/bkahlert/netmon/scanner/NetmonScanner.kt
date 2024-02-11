package com.bkahlert.netmon.scanner

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.time.Now
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.enrichment.Enricher
import com.bkahlert.netmon.net.cidr
import com.bkahlert.netmon.net.networkInterface
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import com.bkahlert.netmon.nmap.TimingTemplate
import java.net.InterfaceAddress
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NetmonScanner(
    val interfaceAddress: InterfaceAddress,
    val scanner: NmapNetworkScanner,
    vararg val enrichers: Enricher<Host>,
    val onScan: (ScanResult) -> Unit,
    val onChange: (Host) -> Unit,
    val `interface`: String = checkNotNull(interfaceAddress.networkInterface).name,
    val cidr: Cidr = interfaceAddress.cidr,
    val scanResultFile: Path = Paths.get("scan.$`interface`.${cidr.filenameString}.json"),
    val scanInterval: Duration = 10.seconds,
) {
    private val logger by SLF4J

    private fun scanInitially(): ScanResult {
        logger.info("Performing initial scan...")
        return ScanResult(
            `interface` = `interface`,
            cidr = cidr,
            hosts = scanner.scan(cidr, timingTemplate = TimingTemplate.Insane),
            timestamp = Now,
        )
    }

    fun scan() {
        val oldScan = ScanResult.load(scanResultFile) ?: scanInitially()

        val currentScan = ScanResult(
            `interface` = `interface`,
            cidr = cidr,
            hosts = scanner.scan(cidr).map { host -> enrichers.fold(host) { acc, enricher -> enricher.enrich(acc) ?: acc } },
            timestamp = Now,
        )

        oldScan.merge(currentScan, onChange)
            .also { onScan(it) }
            .also { it.save(scanResultFile) }

        Thread.sleep(scanInterval.inWholeMilliseconds)
    }

    override fun toString(): String = "network-scanner-$`interface`-$cidr"
}
