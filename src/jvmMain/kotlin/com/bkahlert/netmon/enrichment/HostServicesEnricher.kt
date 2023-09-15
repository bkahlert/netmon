package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache

/** An enricher that contributes the [Host.services] using the specified [serviceInfoCache] to a [Host]. */
class HostServicesEnricher(
    private val serviceInfoCache: JmDNSServiceInfoCache,
) : HostPropertyEnricher<Set<String>>(Host::services) {
    override fun resolve(entity: Host): Set<String>? = serviceInfoCache.services(entity.ip)?.map { it.application }?.toSet()
}
