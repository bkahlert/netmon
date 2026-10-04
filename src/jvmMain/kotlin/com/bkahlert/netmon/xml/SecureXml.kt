package com.bkahlert.netmon.xml

import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader

/** An XML reader that resolves neither DTDs nor external entities. */
internal object SecureXml {

    private val factory: XMLInputFactory = XMLInputFactory.newDefaultFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    }

    fun reader(xml: String): XMLStreamReader = factory.createXMLStreamReader(xml.reader())
}
