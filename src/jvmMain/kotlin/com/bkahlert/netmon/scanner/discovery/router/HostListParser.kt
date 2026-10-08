package com.bkahlert.netmon.scanner.discovery.router

import com.bkahlert.netmon.scanner.support.xml.SecureXml
import java.io.InputStream
import javax.xml.stream.XMLStreamConstants

/** Reads the XML list behind `X_AVM-DE_GetHostListPath`, one `Item` at a time. */
object HostListParser {

    fun parse(input: InputStream): List<RouterHost> = buildList {
        val reader = SecureXml.reader(input)
        try {
            var fields: MutableMap<String, String>? = null
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> when {
                        reader.localName == "Item" -> fields = mutableMapOf()
                        fields != null -> fields[reader.localName] = reader.elementText.trim()
                    }
                    XMLStreamConstants.END_ELEMENT -> if (reader.localName == "Item") {
                        fields?.let { add(it.toRouterHost()) }
                        fields = null
                    }
                }
            }
        } finally {
            reader.close()
        }
    }

    fun Map<String, String>.toRouterHost() = RouterHost(
        mac = this["MACAddress"]?.lowercase()?.takeIf { it.isNotEmpty() },
        ip = this["IPAddress"]?.takeIf { it.isNotEmpty() },
        active = this["Active"] == "1",
        hostName = this["HostName"]?.takeIf { it.isNotEmpty() },
        friendlyName = this["X_AVM-DE_FriendlyName"]?.takeIf { it.isNotEmpty() },
        interfaceType = this["InterfaceType"]?.takeIf { it.isNotEmpty() },
        speed = this["X_AVM-DE_Speed"]?.toIntOrNull() ?: 0,
        port = this["X_AVM-DE_Port"]?.toIntOrNull() ?: 0,
        guest = this["X_AVM-DE_Guest"] == "1",
        deviceClass = this["X_AVM-DE_DeviceClass"]?.takeIf { it.isNotEmpty() },
        deviceClassUser = this["X_AVM-DE_DeviceClassUser"]?.takeIf { it.isNotEmpty() },
    )
}
