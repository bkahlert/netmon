package com.bkahlert.netmon.scanner.discovery.mdns

import com.bkahlert.netmon.contract.IP
import java.net.Inet4Address
import java.net.InetAddress

fun service(
    application: String,
    name: String,
    server: String? = "$name.local.",
    port: Int = 80,
    ip: String = "10.0.0.1",
    vararg txt: Pair<String, String>,
): ServiceInfo = ServiceInfo(
    type = "_$application._tcp.local.",
    typeWithSubtype = "_$application._tcp.local.",
    subtype = null,
    application = application,
    protocol = "tcp",
    domain = "local",
    name = name,
    qualifiedName = "$name._$application._tcp.local.",
    serviceRecord = server?.let { ServiceInfo.ServiceRecord(priority = 0, weight = 0, port = port, target = it) },
    inet4Addresses = listOf(InetAddress.getByName(ip) as Inet4Address),
    inet6Addresses = emptyList(),
    properties = txt.associate { (key, value) -> key to property(key, value.toByteArray(), value) },
)

fun binaryProperty(key: String, bytes: ByteArray): Pair<String, ServiceInfo.Property> = key to property(key, bytes, String(bytes, Charsets.ISO_8859_1))

fun ServiceInfo.withProperty(entry: Pair<String, ServiceInfo.Property>): ServiceInfo = copy(properties = properties + entry)

private fun property(key: String, raw: ByteArray, string: String) = object : ServiceInfo.Property {
    override val name: String = key
    override val bytes: ByteArray = raw
    override val text: String = string
}

class FakeMdns(private vararg val all: ServiceInfo) : MdnsLookup {
    override fun servers(ip: IP): Set<String>? = services(ip)?.mapNotNull { it.serviceRecord?.target }?.toSet()?.takeIf { it.isNotEmpty() }
    override fun services(ip: IP): Set<ServiceInfo>? = all.filter { info -> info.inet4Addresses.any { IP.of(it.address) == ip } }.toSet().takeIf { it.isNotEmpty() }
    override fun services(application: String): List<ServiceInfo> = all.filter { it.application == application }
}
