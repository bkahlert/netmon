package com.bkahlert.netmon.scanner.nmap

import com.bkahlert.netmon.contract.Cidr
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.scanner.scan.NetworkScan
import com.bkahlert.netmon.scanner.scan.ScanMode

class NmapScanAdapter(
    private val run: (Cidr, TimingTemplate) -> List<Host>,
) : NetworkScan {

    override fun scan(network: Cidr, mode: ScanMode): List<Host> = run(
        network,
        when (mode) {
            ScanMode.INITIAL -> TimingTemplate.Insane
            ScanMode.NORMAL -> TimingTemplate.Aggressive
        },
    )
}
