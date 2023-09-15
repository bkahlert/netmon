package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache

/** An enricher that contributes device information to a [Host] using the specified [serviceInfoCache]. */
class DeviceInfoHostEnricher(
    private val serviceInfoCache: JmDNSServiceInfoCache,
) : HostEnricher {
    override fun enrich(entity: Host): Host? = serviceInfoCache.services(entity.ip)?.firstOrNull { it.application == "device-info" }?.let {
        entity.copy(
            name = it.name,
            model = it.properties["model"]?.text ?: entity.model,
        )
    }

    override fun toString(): String = this::class.simpleName ?: "<object>"
}
