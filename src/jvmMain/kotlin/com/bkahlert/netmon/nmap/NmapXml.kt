package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.xml.SecureXml
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** nmap's XML output (`-oX`) read into [Host] instances. */
object NmapXml {

    /**
     * Returns the hosts of the nmap run in [xml], in document order.
     *
     * A host without an IPv4 or IPv6 address is left out. The name is the first `hostname` element's name; the vendor
     * and the MAC (lowercase) come from the MAC address element. Each is `null` when absent.
     *
     * A MAC that the run reports for several hosts identifies none of them: a Bonjour sleep proxy answers ARP for a
     * sleeping device with its own MAC. Those hosts have no MAC.
     */
    fun parse(xml: String): List<Host> {
        val reader = SecureXml.reader(xml)
        try {
            val hosts = buildList {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "host") {
                        reader.readHost()?.let(::add)
                    }
                }
            }
            val sharedMacs = hosts.mapNotNull { it.mac }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            return hosts.map { if (it.mac in sharedMacs) it.copy(mac = null) else it }
        } finally {
            reader.close()
        }
    }

    private fun XMLStreamReader.readHost(): Host? {
        var state: String? = null
        var address: String? = null
        var vendor: String? = null
        var mac: String? = null
        var name: String? = null
        var depth = 1
        while (depth > 0 && hasNext()) {
            when (next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    when (localName) {
                        "status" -> state = getAttributeValue(null, "state")
                        "address" -> when (getAttributeValue(null, "addrtype")) {
                            "ipv4", "ipv6" -> if (address == null) address = getAttributeValue(null, "addr")
                            "mac" -> {
                                vendor = getAttributeValue(null, "vendor")
                                mac = getAttributeValue(null, "addr")?.lowercase()
                            }
                        }
                        "hostname" -> if (name == null) name = getAttributeValue(null, "name")
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
        return address?.let { Host(ip = IP.of(it), name = name, status = state?.let(Status::of), vendor = vendor, mac = mac) }
    }
}
