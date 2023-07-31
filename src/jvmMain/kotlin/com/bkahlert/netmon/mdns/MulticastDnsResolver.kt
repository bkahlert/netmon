package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.entries
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.mdns.DerivedValue.Companion.derived
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
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
    private val services = mutableMapOf<Pair<String, String>, ServiceInfo>()
    private fun addService(event: ServiceEvent) {
        val info = event.info ?: return
        logger.info("Adding service: {}", entries("name" to event.name, "type" to event.type))
        servicesLock.withLock { services[event.name to event.type] = info }
    }

    private fun removeService(event: ServiceEvent) {
        logger.info("Removing service: {}", entries("name" to event.name, "type" to event.type))
        servicesLock.withLock { services.remove(event.name to event.type) }
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

    private val serverToServices: Map<String, Set<ServiceInfo>> by { services } derived {
        servicesLock.withLock { it.values.toList() }
            .filter { it.hasServer() }
            .groupBy { it.server }
            .mapValues { (_, infos) -> buildSet { addAll(infos) } }
    }

    private val serverToIpAddresses: Map<String, Set<IP>> by { serverToServices } derived {
        it.mapValues { (_, infos) ->
            buildSet { infos.forEach { info -> addAll(info.ipAddresses) } }
        }
    }

    private val ipAddressToServers: Map<IP, Set<String>> by { serverToIpAddresses } derived {
        buildSet { it.values.forEach { addAll(it) } }
            .associateWith { ip -> it.filterValues { it.contains(ip) }.keys }
            .mapValues { (_, hostnames) -> hostnames.toSet() }
    }

    private val ipAddressToServices: Map<IP, Set<ServiceInfo>> by { ipAddressToServers to serverToServices } derived { (ipToSrv, srvToSrv) ->
        ipToSrv.mapValues { (_, servers) ->
            buildSet { servers.forEach { addAll(srvToSrv[it].orEmpty()) } }
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
        ipAddressToServices.entries.joinTo(this, ", ") { (ip, services) ->
            val servicePart = "[${services.joinToString(",") { it.application }}]"
            val hostPart = "[${ipAddressToServers[ip].orEmpty().joinToString(",") { it }}]"
            "$ip=$servicePart@$hostPart"
        }
        append(")")
    }

    fun resolveHostname(ip: IP, removeRoot: Boolean = true): String? = ipAddressToServers[ip]?.firstOrNull()
        ?.let { if (removeRoot) it.removeSuffix(".") else it }

    fun resolveModel(ip: IP): String? = ipAddressToServices[ip]?.firstNotNullOfOrNull { it.properties["model"] }

    fun resolveServices(ip: IP): List<String> = ipAddressToServices[ip].orEmpty().map { it.application }

    companion object {

        val logger by SLF4J
        fun MulticastDnsResolver.debug() = runCatching {
            val json = Json {
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
                prettyPrint = true
                serializersModule = SerializersModule {
                    contextual(ServiceInfoSerializer)
                }
            }
            println("serverToServices: ${json.encodeToString(serverToServices)}")
            println("serverToIpAddresses: ${json.encodeToString(serverToIpAddresses)}")
            println("ipAddressToServers: ${json.encodeToString(ipAddressToServers)}")
            println("ipAddressToServices: ${json.encodeToString(ipAddressToServices)}")
            println(toString())
        }.onFailure { logger.error("Unexpected error", it) }

    }
}

// currently only used for debugging as the toString of ServiceInfo is a nightmare
data object ServiceInfoSerializer : KSerializer<ServiceInfo> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ServiceInfo", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ServiceInfo) {
        val str = listOf(
            "name" to value.name,
            "type" to value.type,
            "subtype" to value.subtype,
            "application" to value.application,
            "server" to value.server,
            "uRLs" to value.urLs.joinToString(", "),
            "properties" to value.properties.toString(),
        ).filter { (_, value) -> value != null && value.toString().isNotBlank() }
            .joinToString("; ") { (key, value) ->
                "$key: $value"
            }
        encoder.encodeString(str)
    }

    override fun deserialize(decoder: Decoder): ServiceInfo =
        error("Deserialization not supported")
}
