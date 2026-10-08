package com.bkahlert.netmon.scanner.scan.enrichment

import com.bkahlert.netmon.scanner.support.logging.SLF4J
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed
import com.bkahlert.netmon.contract.Status
import kotlin.time.Instant
import kotlin.reflect.KProperty1

/** An enricher that contributes additional information to a [Host]. */
typealias HostEnricher = Enricher<Host>


/** An enricher that contributes additional information to a [Host]. */
abstract class HostPropertyEnricher<V>(
    private val property: KProperty1<Host, V?>,
) : Enricher<Host> {
    protected val logger by SLF4J

    /**
     * Resolves the value to be contributed to the [Host].
     *
     * If `null` is returned, no contribution is made.
     */
    abstract fun resolve(entity: Host): V?

    override fun enrich(entity: Host): Host? {
        val currentValue = property.get(entity)
        return if (currentValue == null) {
            val value = resolve(entity)
            if (value != null) {
                entity.copy(property, value).also {
                    logger.info("{} enriched: {}={}", it, property.name, value)
                }
            } else {
                null
            }
        } else {
            logger.debug("{} already enriched: {}={}", entity, property.name, currentValue)
            null
        }
    }

    override fun toString(): String = this::class.simpleName ?: "<object>"

    companion object {
        /** Returns a copy of this [Host] with the specified [property] set to the specified [value]. */
        @Suppress("UNCHECKED_CAST")
        fun <V> Host.copy(property: KProperty1<Host, V>, value: V): Host = copy(
            ip = if (property == Host::ip && value is IP) value else ip,
            name = if (property == Host::name && value is String) value else name,
            status = if (property == Host::status && value is Status) value else status,
            since = if (property == Host::since && value is Instant) value else since,
            model = if (property == Host::model && value is String) value else model,
            vendor = if (property == Host::vendor && value is String) value else vendor,
            services = if (property == Host::services && value is Set<*>) value as Set<String> else services,
            mac = if (property == Host::mac && value is String) value else mac,
            kind = if (property == Host::kind && value is Kind) value else kind,
            link = if (property == Host::link && value is Link) value else link,
            speed = if (property == Host::speed && value is LinkSpeed) value else speed,
        )
    }
}
