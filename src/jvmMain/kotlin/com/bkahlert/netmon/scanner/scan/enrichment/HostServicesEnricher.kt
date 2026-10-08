package com.bkahlert.netmon.scanner.scan.enrichment

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.scanner.discovery.mdns.MdnsLookup

/** An enricher that contributes the [Host.services] using the specified [mdnsLookup] to a [Host]. */
class HostServicesEnricher(
    private val mdnsLookup: MdnsLookup,
) : HostPropertyEnricher<Set<String>>(Host::services) {
    override fun resolve(entity: Host): Set<String>? = mdnsLookup.services(entity.ip)?.map { it.application }?.toSet()
}
