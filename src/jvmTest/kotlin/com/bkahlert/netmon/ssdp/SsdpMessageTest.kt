package com.bkahlert.netmon.ssdp

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class SsdpMessageTest {

    @Test
    fun a_search_response_yields_location_server_and_max_age() {
        val text = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=100\r\nLOCATION: http://192.168.17.5:80/description.xml\r\nSERVER: Bridge/1.0 UPnP/1.0 TestBridge/1.78.0\r\nST: upnp:rootdevice\r\nUSN: uuid:00000000-0000-4000-8000-000000000001::upnp:rootdevice\r\n\r\n"

        val result = SsdpMessage.parse(text)

        result.shouldNotBeNullAnd {
            it.location shouldBe "http://192.168.17.5:80/description.xml"
            it.server shouldBe "Bridge/1.0 UPnP/1.0 TestBridge/1.78.0"
            it.notificationType shouldBe "upnp:rootdevice"
            it.usn shouldBe "uuid:00000000-0000-4000-8000-000000000001::upnp:rootdevice"
            it.maxAge shouldBe 100.seconds
        }
    }

    @Test
    fun a_notify_uses_nt_and_lowercase_header_names_are_fine() {
        val text = "NOTIFY * HTTP/1.1\r\nHost: 239.255.255.250:1900\r\nnt: urn:schemas-upnp-org:device:NAS:1\r\nnts: ssdp:alive\r\nlocation: http://192.168.16.10:49152/gatedesc.xml\r\n\r\n"

        val result = SsdpMessage.parse(text)

        result.shouldNotBeNullAnd {
            it.notificationType shouldBe "urn:schemas-upnp-org:device:NAS:1"
            it.location shouldBe "http://192.168.16.10:49152/gatedesc.xml"
            it.maxAge.shouldBeNull()
        }
    }

    @Test
    fun a_notify_with_nts_byebye_is_a_byebye_and_an_alive_or_a_response_is_not() {
        val byebye = SsdpMessage.parse("NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\nNTS: ssdp:byebye\r\nUSN: uuid:00000000-0000-4000-8000-000000000001::upnp:rootdevice\r\n\r\n")
        val alive = SsdpMessage.parse("NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\nNTS: ssdp:alive\r\n\r\n")
        val response = SsdpMessage.parse("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n\r\n")

        byebye.shouldNotBeNullAnd { it.isByebye shouldBe true }
        alive.shouldNotBeNullAnd { it.isByebye shouldBe false }
        response.shouldNotBeNullAnd { it.isByebye shouldBe false }
    }

    @Test
    fun a_search_request_and_garbage_are_not_messages() {
        SsdpMessage.parse("M-SEARCH * HTTP/1.1\r\nST: ssdp:all\r\n\r\n").shouldBeNull()
        SsdpMessage.parse("not http").shouldBeNull()
    }
}

private fun SsdpMessage?.shouldNotBeNullAnd(block: (SsdpMessage) -> Unit) {
    checkNotNull(this) { "expected a message" }
    this should block
}
