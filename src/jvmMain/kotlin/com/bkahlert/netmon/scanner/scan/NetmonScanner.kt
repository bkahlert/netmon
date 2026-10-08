package com.bkahlert.netmon.scanner.scan

import com.bkahlert.netmon.scanner.support.logging.SLF4J
import kotlin.time.Clock
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.scanner.scan.enrichment.Enricher
import kotlin.time.Duration
import com.bkahlert.netmon.scanner.state.ScanStateStore

class NetmonScanner(
    val context: NetworkContext,
    val scanner: NetworkScan,
    val enrichers: List<Enricher<Host>>,
    val state: ScanStateStore,
    private val clock: Clock,
    private val downAfter: Duration,
    val onScan: (ScanResult) -> Unit,
    val onChange: (Host) -> Unit,
) {
    private val logger by SLF4J

    private val restartFloor = RestartFloor()

    private fun scanInitially(): ScanResult {
        logger.info("Performing initial scan...")
        return ScanResult(
            `interface` = context.interfaceName,
            cidr = context.cidr,
            hosts = scanner.scan(context.cidr, ScanMode.INITIAL),
            timestamp = clock.now(),
        )
    }

    fun scan() {
        val oldScan = state.load() ?: scanInitially()

        val currentScan = ScanResult(
            `interface` = context.interfaceName,
            cidr = context.cidr,
            hosts = scanner.scan(context.cidr, ScanMode.NORMAL).map { host ->
                enrichers.fold(host) { acc, enricher -> enricher.enrich(acc) ?: acc }
            },
            timestamp = clock.now(),
        )

        val merged = oldScan.merge(currentScan, downAfter = downAfter, notBefore = restartFloor.at(currentScan.timestamp))
        merged.changedHosts.forEach(onChange)
        onScan(merged.scan)
        state.save(merged.scan)
    }

    override fun toString(): String = "network-scanner-${context.interfaceName}-${context.cidr}"
}
