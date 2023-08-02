package com.bkahlert.netmon

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.time.Now
import com.bkahlert.netmon.mdns.MulticastDnsResolver
import com.bkahlert.netmon.nmap.NmapNetworkScanner
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// TODO parameters should use interface types
class NetmonScanner(
    val network: Cidr,
    val scanner: NmapNetworkScanner,
    val resolver: MulticastDnsResolver,
    val onScan: (ScanResult) -> Unit,
    val onChange: (Host) -> Unit,
    val scanResultFile: Path = Paths.get(".netmon.scan.${network.filenameString}.json"),
    val scanInterval: Duration = 5.seconds,
) : Thread("netmon-scanner-$network") {

    private val logger by SLF4J

    init {
        logger.info("Starting netmon-scanner {}, {}", kv("network", network), kv("scanner", scanner))
    }

    override fun run() {
        var oldScan = ScanResult.load(scanResultFile) ?: run {
            logger.info("Performing initial scan...")
            val aggressiveScanner = NmapNetworkScanner(timingTemplate = ScanResult.TimingTemplate.Aggressive)
            ScanResult(
                network = network,
                hosts = aggressiveScanner.scan(network).map { (ip, name, vendor, status) ->
                    Host(
                        ip = ip,
                        name = name,
                        status = status,
                        vendor = vendor,
                    )
                },
                timestamp = Now,
            )
        }

        while (!interrupted()) {
            val currentScan = ScanResult(
                network = network,
                hosts = scanner.scan(network).map { (ip, name, vendor, status) ->
                    Host(
                        ip = ip,
                        name = name ?: resolver.resolveHostname(ip),
                        status = status,
                        model = resolver.resolveModel(ip),
                        vendor = vendor,
                        services = resolver.resolveServices(ip),
                    )
                },
                timestamp = Now,
            )
            oldScan = oldScan.merge(currentScan, onChange)
                .also { onScan(it) }
                .also { it.save(scanResultFile) }

            try {
                sleep(scanInterval.inWholeMilliseconds)
            } catch (e: InterruptedException) {
                resolver.close()
                // Restore the interrupted status so we exit the loop
                currentThread().interrupt()
            }
        }
    }
}
