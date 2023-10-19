package com.bkahlert.netmon.scanner

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.time.Now
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.enrichment.Enricher
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import com.bkahlert.netmon.nmap.TimingTemplate
import com.bkahlert.netmon.scanner.InterfaceResolver.Companion.networkInterface
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
) : Thread("scnr-$`interface`-$cidr") {

    private val logger by SLF4J

    override fun run() {
        logger.info("Starting netmon-scanner on {}, {}", kv(interfaceAddress), kv("scanner", scanner))
        var oldScan = ScanResult.load(scanResultFile) ?: run {
            logger.info("Performing initial scan...")
            ScanResult(
                `interface` = `interface`,
                cidr = cidr,
                hosts = scanner.scan(cidr, timingTemplate = TimingTemplate.Insane),
                timestamp = Now,
            )
        }

        while (!interrupted()) {
            val currentScan = ScanResult(
                `interface` = `interface`,
                cidr = cidr,
                hosts = scanner.scan(cidr).map { host -> enrichers.fold(host) { acc, enricher -> enricher.enrich(acc) ?: acc } },
                timestamp = Now,
            )
            oldScan = oldScan.merge(currentScan, onChange)
                .also { onScan(it) }
                .also { it.save(scanResultFile) }

            try {
                sleep(scanInterval.inWholeMilliseconds)
            } catch (e: InterruptedException) {
                // Restore the interrupted status so we exit the loop
                currentThread().interrupt()
            }
        }
    }
}
