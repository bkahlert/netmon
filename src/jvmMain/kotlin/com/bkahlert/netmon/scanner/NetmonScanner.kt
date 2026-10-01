package com.bkahlert.netmon.scanner

import com.bkahlert.kommons.logging.SLF4J
import kotlinx.datetime.Clock
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

class NetmonScanner(
    val interfaceAddress: InterfaceAddress,
    val scanner: NmapNetworkScanner,
    vararg val enrichers: Enricher<Host>,
    val onScan: (ScanResult) -> Unit,
    val onChange: (Host) -> Unit,
) {
    private val logger by SLF4J

    val `interface`: String = checkNotNull(interfaceAddress.networkInterface).name
    val cidr: Cidr = interfaceAddress.cidr
    val scanResultFile: Path = Paths.get("scan.$`interface`.${cidr.filenameString}.json")

    private fun scanInitially(): ScanResult {
        logger.info("Performing initial scan...")
        return ScanResult(
            `interface` = `interface`,
            cidr = cidr,
            hosts = scanner.scan(cidr, timingTemplate = TimingTemplate.Insane),
            timestamp = Clock.System.now(),
        )
    }

    fun scan() {
        val oldScan = ScanResult.load(scanResultFile) ?: scanInitially()

        val currentScan = ScanResult(
            `interface` = `interface`,
            cidr = cidr,
            hosts = scanner.scan(cidr).map { host ->
                enrichers.fold(host) { acc, enricher -> enricher.enrich(acc) ?: acc }
            },
            timestamp = Clock.System.now(),
        )

        oldScan.merge(currentScan, onChange)
            .also { onScan(it) }
            .also { it.save(scanResultFile) }
    }

    override fun toString(): String = "network-scanner-$`interface`-$cidr"
}
