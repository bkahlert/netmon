package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.Program
import com.bkahlert.kommons.text.takeUnlessBlank
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.impl.util.ByteWrangler
import kotlin.time.Duration

// JmDNS extensions, since the wording consistency and lack of nullity annotations are the library is a catastrophe

/**
 * Creates a [JmDNS] instance using [JmDNS.create],
 * **and** creates a shutdown hook that calls [JmDNS.close].
 *
 * This seems to have been the original behavior of [JmDNS.create],
 * but in the version `3.5.8` the shutdown hook registration is commented out.
 *
 * @param addr
 *            IP address to bind to.
 * @param name
 *            name of the newly created JmDNS
 * @param threadSleepDuration
 *            time that the JmDNS listener thread should sleep between multicast receives
 * @return jmDNS instance
 */
fun JmDNS(
    addr: InetAddress? = null,
    name: String? = null,
    threadSleepDuration: Duration = Duration.ZERO,
): JmDNS = JmDNS.create(addr, name, threadSleepDuration.inWholeMilliseconds).apply {
    Program.onExit { close() }
}

/**
 * Kotlin-friendly version of [javax.jmdns.ServiceTypeListener].
 */
open class ServiceTypeListener : javax.jmdns.ServiceTypeListener {
    /**
     * A new service type was discovered.
     *
     * @param instance the JmDNS instance which originated the event
     * @param type the service type in the format `_<application>._<protocol>.<domain>.`,
     *             for example `_googlecast._tcp.local.`
     */
    open fun serviceTypeAdded(instance: JmDNS, type: String): Unit = Unit
    final override fun serviceTypeAdded(event: ServiceEvent): Unit = serviceTypeAdded(event.dns, event.type)

    /**
     * A new subtype for the service type was discovered.
     *
     * @param instance the JmDNS instance which originated the event
     * @param typeWithSubtype the service type with subtype in the format `_<subtype>._sub._<application>._<protocol>.<domain>.`,
     *                        for example `_0F5096E8._sub._googlecast._tcp.local.`
     */
    open fun subTypeForServiceTypeAdded(instance: JmDNS, typeWithSubtype: String): Unit = Unit
    final override fun subTypeForServiceTypeAdded(event: ServiceEvent): Unit = subTypeForServiceTypeAdded(event.dns, event.type)

    override fun toString(): String = this::class.simpleName ?: "ServiceTypeListener"
}

/**
 * Kotlin-friendly version of [javax.jmdns.ServiceTypeListener].
 */
open class ServiceListener : javax.jmdns.ServiceListener {
    /**
     * A service has been removed.
     *
     * @param instance the JmDNS instance which originated the event
     * @param type the service type in the format `_<application>._<protocol>.<domain>.`,
     *             for example `_googlecast._tcp.local.`
     * @param name the service instance name, for example: `My Chromecast`
     */
    open fun serviceAdded(instance: JmDNS, type: String, name: String): Unit = Unit
    final override fun serviceAdded(event: ServiceEvent): Unit = serviceAdded(event.dns, event.type, event.name)

    /**
     * A service has been removed.
     *
     * @param instance the JmDNS instance which originated the event
     * @param type the service type in the format `_<application>._<protocol>.<domain>.`,
     *             for example `_googlecast._tcp.local.`
     * @param name the service instance name, for example: `My Chromecast`
     * @param info the service info record
     */
    open fun serviceResolved(instance: JmDNS, type: String, name: String, info: ServiceInfo): Unit = Unit
    final override fun serviceResolved(event: ServiceEvent): Unit = serviceResolved(event.dns, event.type, event.name, ServiceInfo(event.info))

    /**
     * A service has been added.
     *
     * @param instance the JmDNS instance which originated the event
     * @param type the service type in the format `_<application>._<protocol>.<domain>.`,
     *             for example `_googlecast._tcp.local.`
     * @param name the service instance name, for example: `My Chromecast`
     */
    open fun serviceRemoved(instance: JmDNS, type: String, name: String): Unit = Unit
    final override fun serviceRemoved(event: ServiceEvent): Unit = serviceRemoved(event.dns, event.type, event.name)

    override fun toString(): String = this::class.simpleName ?: "ServiceListener"
}

/**
 * Kotlin-friendly version of [javax.jmdns.ServiceInfo]
 * with unified terminology, and added examples.
 */
data class ServiceInfo(
    /**
     * The service type in the format `_[application]._[protocol].[domain].`,
     * for example `_googlecast._tcp.local.`
     */
    val type: String,

    /**
     * The service type with subtype, in any, in the format `_[subtype]._sub._[application]._[protocol].[domain].`,
     * for example `_0F5096E8._sub._googlecast._tcp.local.`,
     * otherwise equal to [type].
     */
    val typeWithSubtype: String,

    /** The service subtype, if any, for example `0F5096E8`. */
    val subtype: String?,

    /** The service application, for example `googlecast`. */
    val application: String,

    /** The service protocol, for example `tcp`. */
    val protocol: String,

    /** The service domain, for example `local`. */
    val domain: String,

    /** The unqualified service name, for example `My Chromecast`. */
    val name: String,

    /** The fully qualified service name, for example `My Chromecast._googlecast._tcp.local.`. */
    val qualifiedName: String,

    /** The service record of this service. */
    val serviceRecord: ServiceRecord?,

    /** The [Inet4Address] instances of this service, for example `[10.0.0.1]` */
    val inet4Addresses: List<Inet4Address>,

    /** The [Inet6Address] instances of this service, for example `[::ffff:0a00:0001]` */
    val inet6Addresses: List<Inet6Address>,

    /** The properties of this service, for example `[id=a61dcbf4f14c4d19ad9811e8f74a6599, ic=/setup/icon.png]` */
    val properties: Map<String, Property>,
) {

    /**
     * Creates a new [ServiceInfo] from the given [info].
     */
    constructor(info: javax.jmdns.ServiceInfo) : this(
        type = info.type,
        typeWithSubtype = info.typeWithSubtype,
        subtype = info.subtype.takeUnlessBlank(),
        application = info.application,
        protocol = info.protocol,
        domain = info.domain,
        name = info.name,
        qualifiedName = info.qualifiedName,
        serviceRecord = info.takeIf { it.hasServer() }?.run {
            ServiceRecord(priority = priority, weight = weight, port = port, target = server)
        },
        inet4Addresses = info.inet4Addresses.asList(),
        inet6Addresses = info.inet6Addresses.asList(),
        properties = info.propertyNames.toList().associateWith { name ->
            object : Property {
                override val name: String = name
                override val bytes: ByteArray = info.getPropertyBytes(name)
                override val text: String = info.getPropertyString(name)
            }
        }
    )

    /** The [InetAddress] instances of this service, for example `[10.0.0.1, ::ffff:0a00:0001]` */
    val inetAddresses: List<InetAddress> by lazy {
        buildList {
            addAll(inet4Addresses)
            addAll(inet6Addresses)
        }
    }

    val urls: List<URI> by lazy {
        inetAddresses.map { address ->
            URI(
                application,
                null,
                if (address is Inet6Address) "[${address.hostAddress}]" else address.hostAddress,
                serviceRecord?.port ?: 80,
                properties["path"]?.text.orEmpty(),
                null,
                null,
            )
        }
    }

    override fun toString(): String = buildString {
        append("ServiceInfo(")
        append("\"")
        append(qualifiedName)
        append("\"")
        if (properties.isNotEmpty()) {
            append(" ")
            properties.entries.joinTo(this, ", ", "{", "}", limit = 3) { (name, property) ->
                "$name=${property.text}"
            }
        }
        append(" by ")
        append(serviceRecord)
        append(" at ")
        inetAddresses.joinTo(this, ", ", "[", "]") { it.hostAddress }
        append(")")
    }

    /** The SRV records of this service, for example `_service._proto.name. ttl IN SRV [priority] [weight] [port] [target].` */
    data class ServiceRecord(
        /** The priority of the [target] host, lower value means more preferred, for example: `10` */
        val priority: Int,

        /** The relative weight for records with the same priority, higher value means higher chance of getting picked, for example: `60` */
        val weight: Int,

        /** The TCP or UDP port on which the service is to be found, for example: `8010` */
        val port: Int,

        /** The canonical hostname of the machine providing the service, ending in a dot, for example: `foo.local.` */
        val target: String,
    ) {
        override fun toString(): String = "${target.removeSuffix(".")}:$port"
    }

    interface Property {
        val name: String
        val bytes: ByteArray
        val text: String
    }
}

/** Contains the [ServiceInfo] of the `device-info` application. */
val Iterable<ServiceInfo>.deviceInfo: ServiceInfo?
    get() = firstOrNull { it.application == "device-info" }

/**
 * Kotlin-idiomatic [javax.jmdns.ServiceInfo.create] variant.
 *
 * @param name unqualified service instance name, such as <code>foobar</code>
 * @param type fully qualified service type name, such as <code>_http._tcp.local.</code>.
 * @param subtype service subtype see draft-cheshire-dnsext-dns-sd-06.txt chapter 7.1 Selective Instance Enumeration
 * @param port the local port on which the service runs
 * @param weight weight of the service
 * @param priority priority of the service
 * @param persistent if <code>true</code> ServiceListener.resolveService will be called whenever new information is received.
 * @param text string describing the service
 * @return new service info
 */
fun serviceInfo(
    name: String,
    type: String,
    subtype: String = "",
    port: Int = 0,
    weight: Int = 0,
    priority: Int = 0,
    persistent: Boolean = false,
    text: String = "",
): javax.jmdns.ServiceInfo = javax.jmdns.ServiceInfo.create(type, name, subtype, port, weight, priority, persistent, text)

/**
 * Kotlin-idiomatic [javax.jmdns.ServiceInfo.create] variant.
 *
 * @param name unqualified service instance name, such as <code>foobar</code>
 * @param type fully qualified service type name, such as <code>_http._tcp.local.</code>.
 * @param subtype service subtype see draft-cheshire-dnsext-dns-sd-06.txt chapter 7.1 Selective Instance Enumeration
 * @param port the local port on which the service runs
 * @param weight weight of the service
 * @param priority priority of the service
 * @param persistent if <code>true</code> ServiceListener.resolveService will be called whenever new information is received.
 * @param props properties describing the service
 * @return new service info
 */
fun serviceInfo(
    name: String,
    type: String,
    subtype: String = "",
    port: Int = 0,
    weight: Int = 0,
    priority: Int = 0,
    persistent: Boolean = false,
    props: Map<String, String>,
): javax.jmdns.ServiceInfo = javax.jmdns.ServiceInfo.create(type, name, subtype, port, weight, priority, persistent, ByteWrangler.textFromProperties(props))

/**
 * Kotlin-idiomatic [javax.jmdns.ServiceInfo.create] variant.
 *
 * @param name unqualified service instance name, such as <code>foobar</code>
 * @param type fully qualified service type name, such as <code>_http._tcp.local.</code>.
 * @param subtype service subtype see draft-cheshire-dnsext-dns-sd-06.txt chapter 7.1 Selective Instance Enumeration
 * @param port the local port on which the service runs
 * @param weight weight of the service
 * @param priority priority of the service
 * @param persistent if <code>true</code> ServiceListener.resolveService will be called whenever new information is received.
 * @param props properties describing the service
 * @return new service info
 */
fun serviceInfo(
    name: String,
    type: String,
    subtype: String = "",
    port: Int = 0,
    weight: Int = 0,
    priority: Int = 0,
    persistent: Boolean = false,
    props: MutableMap<String, String>.() -> Unit,
): javax.jmdns.ServiceInfo = serviceInfo(
    name = name,
    type = type,
    subtype = subtype,
    port = port,
    weight = weight,
    priority = priority,
    persistent = persistent,
    props = buildMap(props),
)

/** The [ServiceInfo.getPropertyNames] and their values. */
val javax.jmdns.ServiceInfo.properties: Map<String, String>
    get() = buildMap {
        propertyNames.iterator().forEach { propertyName ->
            put(propertyName, getPropertyString(propertyName))
        }
    }
