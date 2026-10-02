package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.mdns.JmDNSServiceInfoCache
import com.bkahlert.netmon.mdns.ServiceInfo
import com.bkahlert.netmon.model_identification.DeviceModelCodes

/**
 * An enricher that contributes Apple-specific information to a [Host] using the specified [serviceInfoCache].
 * @see <a href="https://pyatv.dev/documentation/protocols/#companion-link">Companion Link</a>
 */
class AppleHostEnricher(
    private val serviceInfoCache: JmDNSServiceInfoCache,
    private val deviceModelCodes: DeviceModelCodes,
) : HostEnricher {

    private val logger by SLF4J

    override fun toString(): String = buildString {
        append(this::class.simpleName)
        append("(")
        append(deviceModelCodes)
        append(")")
    }

    override fun enrich(entity: Host): Host? = serviceInfoCache.services(entity.ip)?.let { services ->
        when (val model = extractModel(services)) {
            null -> null
            else -> {
                val airplay = services.firstOrNull { it.application == "airplay" }
                entity.copy(
                    model = model,
                    vendor = airplay?.let { it.properties["manufacturer"]?.text ?: "Apple Inc." } ?: entity.vendor,
                )
            }
        }
    }

    fun extractModel(services: Set<ServiceInfo>): String? {
        val modelsFoundBy: Map<String, List<ServiceInfo>> = buildMap {
            services.forEach { service ->
                extractModel(service)
                    .also {
                        if (it.size > 1) {
                            logger.warn(
                                "Multiple models found by service={}: {}",
                                service.application,
                                it
                            )
                        }
                    }
                    .forEach { model ->
                        compute(model) { _, foundBy -> foundBy.orEmpty() + service }
                    }
            }
        }
        return when (modelsFoundBy.size) {
            0 -> null
            1 -> modelsFoundBy.entries.first()
                .also {
                    logger.info(
                        "Unique model={} found: services={}",
                        it.key,
                        it.value.map(ServiceInfo::application)
                    )
                }
                .key

            else -> modelsFoundBy.entries.sortedByDescending { it.value.size }
                .also {
                    logger.warn("Multiple models found: {}",
                        modelsFoundBy.mapValues { it.value.map(ServiceInfo::application) })
                }
                .first().key
        }
    }

    fun extractModel(services: ServiceInfo): List<String> =
        services.properties.values.mapNotNull { extractModel(it) }.distinct()

    fun extractModel(serviceProperty: ServiceInfo.Property): String? =
        serviceProperty.text.takeIf { it in deviceModelCodes }
}
