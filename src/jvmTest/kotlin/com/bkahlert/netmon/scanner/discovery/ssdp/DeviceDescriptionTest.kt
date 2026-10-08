package com.bkahlert.netmon.scanner.discovery.ssdp

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DeviceDescriptionTest {

    @Test
    fun reads_the_root_devices_identity_and_ignores_embedded_devices() {
        val xml = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><specVersion><major>1</major><minor>0</minor></specVersion>
            <device><deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType><friendlyName>Test Gateway 1000</friendlyName><manufacturer>Example Corp</manufacturer><modelName>Test Gateway 1000</modelName><modelNumber>1000x</modelNumber><UDN>uuid:00000000-0000-4000-8000-000000000002</UDN>
            <deviceList><device><deviceType>urn:schemas-upnp-org:device:WANDevice:1</deviceType><friendlyName>WANDevice</friendlyName><manufacturer>Other</manufacturer></device></deviceList></device></root>"""

        val result = DeviceDescription.parse(xml.byteInputStream())

        result shouldBe DeviceDescription(
            friendlyName = "Test Gateway 1000",
            manufacturer = "Example Corp",
            modelName = "Test Gateway 1000",
            modelNumber = "1000x",
            deviceType = "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
            udn = "uuid:00000000-0000-4000-8000-000000000002",
        )
    }

    @Test
    fun a_document_without_a_device_is_nothing() {
        DeviceDescription.parse("<root><specVersion/></root>".byteInputStream()).shouldBeNull()
        DeviceDescription.parse("<html><body>422</body></html>".byteInputStream()).shouldBeNull()
        DeviceDescription.parse("garbage".byteInputStream()).shouldBeNull()
    }
}
