package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.scanner.NetworkScan
import com.bkahlert.netmon.scanner.ScanMode

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
