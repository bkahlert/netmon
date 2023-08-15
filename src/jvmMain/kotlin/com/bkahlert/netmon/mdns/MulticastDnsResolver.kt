package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.objects
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.NameResolver
import javax.jmdns.JmDNS

class MulticastDnsResolver(
    private val jmdns: JmDNS,
    private val fallbackResolver: NameResolver? = null,
) : AutoCloseable {

    private val logger by SLF4J
    private val serviceInfoCache = JmDNSServiceInfoCache(jmdns)

    override fun close() {
        serviceInfoCache.close()
        jmdns.close()
    }

    fun resolveHostname(ip: IP): String? {
        logger.debug("Resolving {} hostname with {}", v("ip", ip), kv("cache", serviceInfoCache))
        return serviceInfoCache.hostname(ip)?.also { logger.info("Resolved {} hostname: {}", v("ip", ip), v("hostname", it)) }
            ?: fallbackResolver?.resolve(ip)?.also { logger.info("Fallback-resolved {} hostname: {}", v("ip", ip), v("hostname", it)) }
    }

    fun resolveModel(ip: IP): String? {
        logger.debug("Resolving {} model", v("ip", ip))
        return serviceInfoCache.model(ip)?.also { logger.info("Resolved {} model: {}", v("ip", ip), v("model", it)) }
    }

    fun resolveServices(ip: IP): Set<String> {
        logger.debug("Resolving {} services", v("ip", ip))
        return serviceInfoCache.services(ip).also { logger.info("Resolved {} services: {}", v("ip", ip), objects("services", it)) }
    }

    companion object
}
