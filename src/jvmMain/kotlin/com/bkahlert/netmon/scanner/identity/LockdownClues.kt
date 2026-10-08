package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.scanner.discovery.lockdown.LockdownProbe

/** The model lockdownd reports, for a host whose vendor is unknown or Apple; a fallback when no record names a model. */
class LockdownClues(
    private val probe: LockdownProbe.Lookup,
    private val appleCodes: AppleCodes,
) : ClueSource {

    override fun clues(host: Host): List<Clue> {
        if (!(host.vendor == null || host.vendor.startsWith("Apple", ignoreCase = true))) return emptyList()
        val code = probe.model(host.ip, host.mac)?.let(appleCodes::normalize) ?: return emptyList()
        return listOfNotNull(
            Clue.Model(code, Source.APPLE_CODE),
            Clue.Vendor("Apple", Source.APPLE_CODE),
            appleCodes.kindOf(code)?.let { Clue.DeviceKind(it, Source.APPLE_CODE) },
        )
    }
}
