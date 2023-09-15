package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache

/** An enricher that contributes the [Host.name] using the specified [serviceInfoCache] to a [Host]. */
class HostNameEnricher(
    private val serviceInfoCache: JmDNSServiceInfoCache,
) : HostPropertyEnricher<String>(Host::name) {
    override fun resolve(entity: Host): String? = serviceInfoCache.servers(entity.ip)?.firstOrNull()
}
