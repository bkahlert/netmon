package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache

/** An enricher that contributes Amazon-specific information to a [Host] using the specified [serviceInfoCache]. */
class AmazonHostEnricher(
    private val serviceInfoCache: JmDNSServiceInfoCache,
) : HostEnricher {
    override fun enrich(entity: Host): Host? = serviceInfoCache.services(entity.ip)?.firstOrNull { it.application == "amzn-wplay" }?.let {
        entity.copy(
            name = it.properties["n"]?.text ?: entity.name,
            model = "Amazon Fire TV",
        )
    }

    override fun toString(): String = this::class.simpleName ?: "<object>"
}
