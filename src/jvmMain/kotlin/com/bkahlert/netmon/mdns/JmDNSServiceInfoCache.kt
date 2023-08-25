package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.entries
import com.bkahlert.netmon.IP
import java.util.concurrent.locks.ReentrantLock
import javax.jmdns.JmDNS
import kotlin.concurrent.withLock

class JmDNSServiceInfoCache(
    private val jmdns: JmDNS,
) : AutoCloseable {

    private val logger by SLF4J

    private val servicesLock = ReentrantLock()
    private val services: MutableMap<Pair<String, String>, ServiceInfo> = mutableMapOf()
    private var mappings: ResolveMappings = ResolveMappings(emptyList())

    private fun addService(type: String, name: String, info: ServiceInfo) {
        logger.info("Adding service: {}", entries("name" to name, "type" to type))
        servicesLock.withLock {
            services[name to type] = info
            mappings = ResolveMappings(services.values.toList())
        }
    }

    private fun removeService(type: String, name: String) {
        logger.info("Removing service: {}", entries("name" to name, "type" to type))
        servicesLock.withLock {
            services.remove(name to type)
            mappings = ResolveMappings(services.values.toList())
        }
    }

    private val serviceListener = object : ServiceListener() {
        override fun serviceAdded(instance: JmDNS, type: String, name: String) {
            logger.info("Service added: $name.$type")
        }

        override fun serviceResolved(instance: JmDNS, type: String, name: String, info: ServiceInfo) {
            logger.info("Service resolved: $name.$type: $info")
            logger.info("Adding service: {}", entries("name" to name, "type" to type))
            addService(type, name, info)
        }

        override fun serviceRemoved(instance: JmDNS, type: String, name: String) {
            logger.info("Service removed: $name.$type")
            removeService(type, name)
        }
    }

    private val serviceTypeListener = object : ServiceTypeListener() {
        override fun serviceTypeAdded(instance: JmDNS, type: String) {
            instance.addServiceListener(type, serviceListener)
        }
    }

    init {
        // possibly jmDNS keeps announced crap by others alive with the consequence of
        // polluting the network with corrupted zombie service types
        // jmdns.addServiceTypeListener(serviceTypeListener)

        // For now, only register well-known service types
        wellKnownServiceTypes.forEach { jmdns.addServiceListener(it, serviceListener) }
    }

    override fun close() {
        wellKnownServiceTypes.asReversed().forEach { jmdns.removeServiceListener(it, serviceListener) }
        // jmdns.removeServiceTypeListener(serviceTypeListener)
    }

    fun hostname(ip: IP): String? = mappings.ipAddressToServers[ip]?.firstOrNull()?.removeSuffix(".")
    fun model(ip: IP): String? = mappings.ipAddressToServices[ip]?.firstNotNullOfOrNull { it.properties["model"]?.text }
    fun services(ip: IP): Set<String> = buildSet { mappings.ipAddressToServices[ip]?.mapTo(this) { it.application } }

    override fun toString(): String = buildString {
        append(JmDNSServiceInfoCache::class.simpleName)
        append("(")
        append("jmdns=$jmdns")
        append("; services=")
        mappings.ipAddressToServices.entries.joinTo(this, ", ", "[", "]") { (ip, services) ->
            val servicePart = "[${services.joinToString(",") { it.application }}]"
            val hostPart = "[${mappings.ipAddressToServers[ip].orEmpty().joinToString(",") { it }}]"
            "$ip=$servicePart@$hostPart"
        }
        append(")")
    }

    private class ResolveMappings(
        private val services: List<ServiceInfo>,
    ) {

        val serverToServices: Map<String, Set<ServiceInfo>> by lazy {
            services
                .mapNotNull { service ->
                    service.serviceRecord?.let { record -> record.target to service }
                }
                .groupBy { (server, _) -> server }
                .mapValues { (_, infos) ->
                    buildSet { infos.forEach { add(it.second) } }
                }
        }

        val serverToIpAddresses: Map<String, Set<IP>> by lazy {
            serverToServices.mapValues { (_, infos) ->
                buildSet { infos.forEach { info -> info.inetAddresses.mapTo(this) { IP(it) } } }
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

    companion object {
        private val wellKnownServiceTypes: List<String> = listOf(
            "_adisk._tcp.local.",
            "_afpovertcp._tcp.local.",
            "_airport._tcp.local.",
            "_companion-link._tcp.local.",
            "_dacp._tcp.local.",
            "_device-info._tcp.local.",
            "_hap._tcp.local.",
            "_homekit._tcp.local.",
            "_raop._tcp.local.",
            "_rdlink._tcp.local.",
            "_sftp-ssh._tcp.local.",
            "_sleep-proxy._udp.local.",
            "_smb._tcp.local.",
            "_sonos._tcp.local.",
            "_spotify-connect._tcp.local.",
            "_ssh._tcp.local.",
        )
    }
}
