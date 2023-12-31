package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.logging.SLF4J
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

    fun resolveHostname(ip: IP): String? = serviceInfoCache.hostname(ip) ?: fallbackResolver?.resolve(ip)
    fun resolveModel(ip: IP): String? = serviceInfoCache.model(ip)
    fun resolveServices(ip: IP): List<String> = serviceInfoCache.services(ip)

    companion object
}
