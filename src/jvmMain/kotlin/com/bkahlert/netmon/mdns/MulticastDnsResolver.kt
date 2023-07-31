package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.entries
import com.bkahlert.netmon.IP
import java.util.concurrent.locks.ReentrantLock
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.ServiceTypeListener
import kotlin.concurrent.withLock

class MulticastDnsResolver(
    private val jmdns: JmDNS,
) {

    private val logger by SLF4J

    private val servicesLock = ReentrantLock()
    private val services: MutableMap<Pair<String, String>, ServiceInfo> = mutableMapOf()
    private var mappings: ResolveMappings = ResolveMappings(emptyList())

    private fun addService(event: ServiceEvent) {
        val info = event.info ?: return
        logger.info("Adding service: {}", entries("name" to event.name, "type" to event.type))
        servicesLock.withLock {
            services[event.name to event.type] = info
            mappings = ResolveMappings(services.values.toList())
        }
    }

    private fun removeService(event: ServiceEvent) {
        logger.info("Removing service: {}", entries("name" to event.name, "type" to event.type))
        servicesLock.withLock {
            services.remove(event.name to event.type)
            mappings = ResolveMappings(services.values.toList())
        }
    }

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {}
        override fun serviceResolved(event: ServiceEvent) {
            addService(event)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            removeService(event)
        }
    }

    init {
        jmdns.addServiceTypeListener(object : ServiceTypeListener {
            override fun serviceTypeAdded(event: ServiceEvent) {
                jmdns.addServiceListener(event.type, serviceListener)
            }

            override fun subTypeForServiceTypeAdded(event: ServiceEvent) {}
        })
    }

    override fun toString(): String = buildString {
        append("MulticastDnsResolver(")
        mappings.ipAddressToServices.entries.joinTo(this, ", ") { (ip, services) ->
            val servicePart = "[${services.joinToString(",") { it.application }}]"
            val hostPart = "[${mappings.ipAddressToServers[ip].orEmpty().joinToString(",") { it }}]"
            "$ip=$servicePart@$hostPart"
        }
        append(")")
    }

    fun resolveHostname(ip: IP, removeRoot: Boolean = true): String? = mappings.ipAddressToServers[ip]?.firstOrNull()
        ?.let { if (removeRoot) it.removeSuffix(".") else it }

    fun resolveModel(ip: IP): String? = mappings.ipAddressToServices[ip]?.firstNotNullOfOrNull { it.properties["model"] }

    fun resolveServices(ip: IP): List<String> = mappings.ipAddressToServices[ip].orEmpty().map { it.application }

    companion object
}

private class ResolveMappings(
    private val services: List<ServiceInfo>,
) {

    val serverToServices: Map<String, Set<ServiceInfo>> by lazy {
        services
            .filter { it.hasServer() }
            .groupBy { it.server }
            .mapValues { (_, infos) -> buildSet { addAll(infos) } }
    }

    val serverToIpAddresses: Map<String, Set<IP>> by lazy {
        serverToServices.mapValues { (_, infos) ->
            buildSet { infos.forEach { info -> addAll(info.ipAddresses) } }
        }
    }

    val ipAddressToServers: Map<IP, Set<String>> by lazy {
        buildSet { serverToIpAddresses.values.forEach { addAll(it) } }
            .associateWith { ip -> serverToIpAddresses.filterValues { it.contains(ip) }.keys }
            .mapValues { (_, hostnames) -> hostnames.toSet() }
    }

    val ipAddressToServices: Map<IP, Set<ServiceInfo>> by lazy {
        ipAddressToServers.mapValues { (_, servers) ->
            buildSet { servers.forEach { addAll(serverToServices[it].orEmpty()) } }
        }
    }
}
