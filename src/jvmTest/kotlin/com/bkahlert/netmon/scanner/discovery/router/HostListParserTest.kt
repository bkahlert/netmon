package com.bkahlert.netmon.scanner.discovery.router

import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HostListParserTest {

    @Test
    fun reads_every_item_with_its_fields() {
        val result = HostListParser.parse(FakeFritzBox.HOST_LIST.byteInputStream())

        result shouldHaveSize 5
        result[1] should {
            it.mac shouldBe "02:aa:bb:cc:00:17"
            it.ip shouldBe "192.0.2.11"
            it.active shouldBe true
            it.hostName shouldBe "macbookpro-a"
            it.friendlyName shouldBe "MacBook Pro A"
            it.link shouldBe Link.WIFI
            it.linkSpeed shouldBe LinkSpeed(1088)
            it.deviceClass shouldBe "Generic"
        }
    }

    @Test
    fun ethernet_port_and_speed_are_read_and_zero_speed_is_no_speed() {
        val result = HostListParser.parse(FakeFritzBox.HOST_LIST.byteInputStream())

        result[2] should {
            it.link shouldBe Link.ETHERNET
            it.port shouldBe 1
            it.linkSpeed shouldBe LinkSpeed(2500)
        }
        result[3] should {
            it.active shouldBe false
            it.link shouldBe null
            it.linkSpeed shouldBe null
        }
    }

    @Test
    fun user_and_automatic_classes_are_kept_apart() {
        val result = HostListParser.parse(FakeFritzBox.HOST_LIST.byteInputStream())

        result[4].deviceClass shouldBe "Printer"
        result[4].deviceClassUser shouldBe "Storage"
    }

    @Test
    fun a_mac_in_lowercase_and_an_empty_list_are_read() {
        val xml = """<?xml version="1.0"?><List><Item><MACAddress>aa:bb:cc:dd:ee:ff</MACAddress><Active>1</Active></Item></List>"""

        HostListParser.parse(xml.byteInputStream()).single().mac shouldBe "aa:bb:cc:dd:ee:ff"
        HostListParser.parse("""<?xml version="1.0"?><List></List>""".byteInputStream()) shouldHaveSize 0
    }
}
