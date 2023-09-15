package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache

/** An enricher that contributes Sonos-specific information to a [Host] using the specified [serviceInfoCache]. */
class SonosHostEnricher(
    private val serviceInfoCache: JmDNSServiceInfoCache,
) : HostEnricher {
    override fun enrich(entity: Host): Host? = serviceInfoCache.services(entity.ip)?.firstOrNull { it.application == "sonos" }?.let {
        val airplay = serviceInfoCache.services(entity.ip)?.firstOrNull { it.application == "airplay" }
        val raop = serviceInfoCache.services(entity.ip)?.firstOrNull { it.application == "raop" }
        entity.copy(
            name = it.name.substringAfter("@"),
            model = airplay?.properties?.get("model")?.text ?: raop?.properties?.get("am")?.text ?: entity.model,
            vendor = airplay?.properties?.get("manufacturer")?.text ?: entity.vendor,
        )
    }

    override fun toString(): String = this::class.simpleName ?: "<object>"
}
