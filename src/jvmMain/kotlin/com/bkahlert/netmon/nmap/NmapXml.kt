package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** nmap's XML output (`-oX`) read into [Host] instances. */
object NmapXml {

    private val factory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    }

    /**
     * Returns the hosts of the nmap run in [xml], in document order.
     *
     * A host without an IPv4 or IPv6 address is left out. The name is the first `hostname` element's name and the
     * vendor the MAC address's `vendor` attribute; both are `null` when absent.
     */
    fun parse(xml: String): List<Host> {
        val reader = factory.createXMLStreamReader(xml.reader())
        try {
            return buildList {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "host") {
                        reader.readHost()?.let(::add)
                    }
                }
            }
        } finally {
            reader.close()
        }
    }

    private fun XMLStreamReader.readHost(): Host? {
        var state: String? = null
        var address: String? = null
        var vendor: String? = null
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
                            "mac" -> vendor = getAttributeValue(null, "vendor")
                        }
                        "hostname" -> if (name == null) name = getAttributeValue(null, "name")
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
        return address?.let { Host(ip = IP.of(it), name = name, status = state?.let(Status::of), vendor = vendor) }
    }
}
