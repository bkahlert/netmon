import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.NetworkScanner
import com.bkahlert.netmon.NmapNetworkScanner
import com.bkahlert.netmon.ScanResult
import com.bkahlert.netmon.load
import com.bkahlert.netmon.save
import java.nio.file.Path
import java.nio.file.Paths

class NetmonScanner(
    val network: Cidr,
    val scanner: NetworkScanner,
    val onScan: (ScanResult) -> Unit,
    val onChange: (Host) -> Unit,
    val scanResultFile: Path = Paths.get(".netmon.scan.${network.filenameString}.json"),
) : Thread("netmon-scanner-$network") {

    private val logger by SLF4J

    init {
        logger.info("Starting netmon-scanner {}, {}, {}", StructuredArguments.kv("network", network), StructuredArguments.kv("scanner", scanner))
    }

    override fun run() {
        var oldScan = ScanResult.load(scanResultFile) ?: run {
            logger.info("Performing initial scan...")
            NmapNetworkScanner(timingTemplate = ScanResult.TimingTemplate.Aggressive).scan(network)
        }

        while (!interrupted()) {
            val currentScan = scanner.scan(network).also { onScan(it) }
            oldScan = oldScan.merge(currentScan, onChange).also { it.save(scanResultFile) }

            try {
                sleep(1000) // Sleep for 1 second
            } catch (e: InterruptedException) {
                // Restore the interrupted status so we exit the loop
                currentThread().interrupt()
            }
        }
    }
}
