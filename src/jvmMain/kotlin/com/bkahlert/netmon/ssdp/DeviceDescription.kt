package com.bkahlert.netmon.ssdp

import com.bkahlert.netmon.xml.SecureXml
import java.io.InputStream
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

/** The identity fields of the root `device` of a UPnP description. */
data class DeviceDescription(
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val modelNumber: String?,
    val deviceType: String?,
    val udn: String?,
) {
    companion object {
        private val FIELDS = setOf("friendlyName", "manufacturer", "modelName", "modelNumber", "deviceType", "UDN")

        /** Returns the root device's fields, or `null` if [input] is not a description with a `device`. */
        fun parse(input: InputStream): DeviceDescription? {
            val fields = mutableMapOf<String, String>()
            try {
                val reader = SecureXml.reader(input)
                try {
                    var depth = 0
                    var inDevice = false
                    while (reader.hasNext()) {
                        when (reader.next()) {
                            XMLStreamConstants.START_ELEMENT -> {
                                depth++
                                when {
                                    !inDevice && reader.localName == "device" -> inDevice = true
                                    inDevice && depth == 3 && reader.localName in FIELDS -> fields[reader.localName] = reader.elementText.trim().also { depth-- }
                                    inDevice && reader.localName == "deviceList" -> return fields.toDescription()
                                }
                            }
                            XMLStreamConstants.END_ELEMENT -> {
                                depth--
                                if (inDevice && reader.localName == "device") return fields.toDescription()
                            }
                        }
                    }
                } finally {
                    reader.close()
                }
            } catch (e: XMLStreamException) {
                return null
            }
            return fields.toDescription()
        }

        private fun Map<String, String>.toDescription(): DeviceDescription? = if (isEmpty()) null else DeviceDescription(
            friendlyName = this["friendlyName"],
            manufacturer = this["manufacturer"],
            modelName = this["modelName"],
            modelNumber = this["modelNumber"],
            deviceType = this["deviceType"],
            udn = this["UDN"],
        )
    }
}
