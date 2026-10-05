package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.invoke
import com.bkahlert.netmon.mdns.FakeMdns
import com.bkahlert.netmon.mdns.ServiceInfo
import com.bkahlert.netmon.mdns.binaryProperty
import com.bkahlert.netmon.mdns.service
import com.bkahlert.netmon.mdns.withProperty
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import com.bkahlert.netmon.model_identification.load
import com.bkahlert.netmon.model_identification.resource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MdnsCluesTest {

    @Test
    fun a_homekit_camera_gives_name_model_and_kind() {
        val result = clues(host(), service("hap", "IndoorCam 2K-1A38", "Indoorcam.local.", 38757, HOST_IP, "md" to "T8400", "ci" to "17"))

        result shouldContainExactly listOf(
            Clue.Name("IndoorCam 2K-1A38", Source.PROTOCOL),
            Clue.Name("Indoorcam.local.", Source.MDNS_HOST),
            Clue.Model("T8400", Source.PROTOCOL),
            Clue.DeviceKind(Kind.CAMERA, Source.PROTOCOL),
        )
    }

    @Test
    fun a_pi_gives_its_machine_as_model_and_rejects_the_airport_code() {
        val pi = host(vendor = "Raspberry Pi Foundation", mac = "b8:27:eb:66:2e:c2")
        val result = clues(
            pi,
            service("device-info", "Stilgar", "stilgar.local.", 0, HOST_IP, "model" to "AirPort4", "machine" to "Raspberry Pi Zero 2 W Rev 1.0"),
            service("workstation", "stilgar [b8:27:eb:66:2e:c2]", "stilgar.local.", 9, HOST_IP),
        )

        result.filterIsInstance<Clue.Model>() shouldContainExactly listOf(Clue.Model("Raspberry Pi Zero 2 W Rev 1.0", Source.PROTOCOL))
        result shouldContain Clue.DeviceKind(Kind.CIRCUIT_BOARD, Source.PROTOCOL)
        result.filterIsInstance<Clue.Name>().first() shouldBe Clue.Name("Stilgar", Source.PROTOCOL)
    }

    @Test
    fun an_unknown_oui_pi_with_an_airport_code_is_still_a_linux_host() {
        val result = clues(
            host(vendor = null, mac = "d4:d6:df:cd:4a:26"),
            service("device-info", "Checkpoint 32", "checkpoint32.local.", 0, HOST_IP, "model" to "AirPort5", "machine" to "Raspberry Pi Zero Rev 1.3"),
        )

        result.none { it.source == Source.APPLE_CODE } shouldBe true
    }

    @Test
    fun a_fire_tv_stick_with_an_airplay_receiver_app_is_a_set_top_box_named_by_whisperplay() {
        val result = clues(
            host(vendor = "Amazon Technologies", mac = "ec:8a:c4:76:da:f0"),
            service("airplay", "AFTMM-36[AirPlay]", "192-168-17-36.local.", 7000, HOST_IP, "model" to "AppleTV3,1", "rmodel" to "AirReceiver3,1"),
            service("raop", "EC8AC476DAF0@AFTMM-36[AirPlay]", "192-168-17-36.local.", 7000, HOST_IP, "am" to "AppleTV3,1"),
            service("googlecast", "AFTMM-36[Cast]", "192-168-17-36.local.", 8010, HOST_IP, "md" to "AirReceiver", "fn" to "AFTMM-36[Cast]"),
            service("amzn-wplay", "amzn.dmgr:AAC1", "192-168-17-36.local.", 45458, HOST_IP, "n" to "Fire TV Stick 4K"),
        )

        result.filterIsInstance<Clue.Model>() shouldContainExactly listOf(Clue.Model("Fire TV Stick 4K", Source.PROTOCOL))
        result.filterIsInstance<Clue.DeviceKind>().first() shouldBe Clue.DeviceKind(Kind.SET_TOP_BOX, Source.PROTOCOL)
        result.filterIsInstance<Clue.Vendor>().shouldBeEmpty()
    }

    @Test
    fun a_homepod_is_an_apple_speaker_although_its_hap_record_is_a_sensor() {
        val result = clues(
            host(vendor = "Apple Inc.", mac = "f4:34:f0:85:e7:64"),
            service("airplay", "Apple HomePod", "Apple-HomePod.local.", 7000, HOST_IP, "model" to "AudioAccessory5,1"),
            service("hap", "HomePodSensor 294731", "Apple-HomePod.local.", 52427, HOST_IP, "md" to "HomePod", "ci" to "10"),
            service("companion-link", "Apple HomePod", "Apple-HomePod.local.", 49155, HOST_IP, "rpMd" to "AudioAccessory5,1"),
        )

        result shouldContainInOrder listOf(
            Clue.Model("AudioAccessory5,1", Source.APPLE_CODE),
            Clue.Vendor("Apple", Source.APPLE_CODE),
            Clue.DeviceKind(Kind.SPEAKER, Source.APPLE_CODE),
        )
        result shouldContain Clue.Model("HomePod", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.SENSOR, Source.PROTOCOL)
    }

    @Test
    fun a_txt_only_device_info_record_without_srv_still_yields_the_apple_code() {
        val result = clues(
            host(vendor = null, mac = "4a:1d:30:53:a0:b2"),
            service("device-info", "Rabban", server = null, port = 0, ip = HOST_IP, "model" to "iPad7,5"),
            service("companion-link", "Rabban", "Rabban.local.", 52412, HOST_IP, "rpBA" to "E0:E5:D6:4C:AA:FB"),
        )

        result shouldContain Clue.Model("iPad7,5", Source.APPLE_CODE)
        result shouldContain Clue.DeviceKind(Kind.TABLET, Source.APPLE_CODE)
        result shouldContain Clue.Name("Rabban.local.", Source.MDNS_HOST)
    }

    @Test
    fun a_nanoleaf_panel_reads_model_from_its_api_record_and_vendor_from_thread() {
        val result = clues(
            host(vendor = "Nanoleaf", mac = "80:8a:f7:0b:66:32"),
            service("nanoleafapi", "Shapes 6632", "Shapes-6632.local.", 16021, HOST_IP, "md" to "NL42", "id" to "39:25:F5:7C:DE:1B"),
            service("meshcop", "Shapes 6632", "Shapes-6632.local.", 49154, HOST_IP, "vn" to "Nanoleaf", "mn" to "NL42", "nn" to "tado-d8c4"),
        )

        result shouldContain Clue.Name("Shapes 6632", Source.PROTOCOL)
        result.filterIsInstance<Clue.Model>().first() shouldBe Clue.Model("NL42", Source.PROTOCOL)
        result shouldContain Clue.Vendor("Nanoleaf", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.LAMP, Source.PROTOCOL)
    }

    @Test
    fun a_thread_border_router_is_a_hub_without_model_or_vendor() {
        val result = clues(
            host(vendor = "Espressif", mac = "84:fc:e6:4c:9d:98"),
            service("meshcop", "tado-IB1875863552", "tado-IB1875863552.local.", 49154, HOST_IP, "vn" to "OpenThread", "mn" to "BorderRouter"),
        )

        result.filterIsInstance<Clue.Model>().shouldBeEmpty()
        result.filterIsInstance<Clue.Vendor>().shouldBeEmpty()
        result shouldContain Clue.DeviceKind(Kind.HUB, Source.PROTOCOL)
    }

    @Test
    fun a_tv_takes_its_category_first_and_its_cast_model() {
        val result = clues(
            host(vendor = "LG Electronics", mac = "00:a1:59:1b:dc:28"),
            service("hap", "LG webOS TV 77BF", "LGwebOSTV.local.", 38461, HOST_IP, "md" to "LG webOS TV", "ci" to "31"),
            service("googlecast", "OLED65C47LA.DEUQDJP-b8be", "b8be523d.local.", 8009, HOST_IP, "md" to "OLED65C47LA.DEUQDJP", "fn" to "[LG] webOS TV OLED65C47LA"),
            service("airplay", "[LG] webOS TV OLED65C47LA", "LGwebOSTV.local.", 7000, HOST_IP, "model" to "OLED65C47LA.DEUQDJP", "manufacturer" to "LG"),
        )

        result.filterIsInstance<Clue.DeviceKind>().first() shouldBe Clue.DeviceKind(Kind.TELEVISION, Source.PROTOCOL)
        result.filterIsInstance<Clue.Model>().map { it.value } shouldContainInOrder listOf("LG webOS TV", "OLED65C47LA.DEUQDJP")
        result shouldContain Clue.Name("[LG] webOS TV OLED65C47LA", Source.PROTOCOL)
        result shouldContain Clue.Vendor("LG", Source.PROTOCOL)
    }

    @Test
    fun a_printer_reads_type_make_and_instance_name() {
        val result = clues(
            host(vendor = "HP", mac = "84:69:93:d9:6f:f6"),
            service("ipp", "HP LaserJet M110w (D96FF6)", "NPID96FF6.local.", 631, HOST_IP, "ty" to "HP LaserJet M109-M112", "usb_MFG" to "HP", "usb_MDL" to "HP LaserJet M109-M112"),
        )

        result shouldContain Clue.Name("HP LaserJet M110w (D96FF6)", Source.PROTOCOL)
        result shouldContain Clue.Model("HP LaserJet M109-M112", Source.PROTOCOL)
        result shouldContain Clue.Vendor("HP", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.PRINTER, Source.PROTOCOL)
    }

    @Test
    fun a_sonos_speaker_keeps_its_marketing_model_and_the_name_after_the_at_sign() {
        val result = clues(
            host(vendor = "Sonos", mac = "48:a6:b8:16:41:18"),
            service("airplay", "Sonos One SL", "Sonos-48A6B8164118.local.", 7000, HOST_IP, "model" to "One SL", "manufacturer" to "Sonos"),
            service("sonos", "RINCON_48A6B816411801400@Sonos One SL", "Sonos-48A6B8164118.local.", 1443, HOST_IP),
        )

        result.filterIsInstance<Clue.Name>().map { it.value } shouldContainInOrder listOf("Sonos One SL", "Sonos One SL", "Sonos-48A6B8164118.local.")
        result shouldContain Clue.Model("One SL", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.SPEAKER, Source.PROTOCOL)
    }

    @Test
    fun an_ewelink_plug_is_a_socket() {
        val result = clues(host(vendor = "Espressif"), service("ewelink", "eWeLink_100219ac26", "4831B7A07A90.local.", 8081, HOST_IP, "type" to "plug"))

        result shouldContain Clue.DeviceKind(Kind.SOCKET, Source.PROTOCOL)
    }

    @Test
    fun a_fire_tv_stick_announcing_matter_casting_is_a_set_top_box_named_by_its_dn() {
        val result = clues(
            host(vendor = "Amazon Technologies"),
            service("matterd", "ABD9633D76CEA931", "EC8AC43F4FBA.local.", 5550, HOST_IP, "DN" to "Fire TV Stick", "DT" to "35"),
        )

        result shouldContain Clue.Model("Fire TV Stick", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.SET_TOP_BOX, Source.PROTOCOL)
    }

    @Test
    fun binary_txt_values_are_left_alone() {
        val meshcop = service("meshcop", "LED C062", "Shoe-Box-C062.local.", 49154, HOST_IP, "vn" to "Nanoleaf", "mn" to "SQFX01")
            .withProperty(binaryProperty("at", byteArrayOf(0, 0, 0, 0, 0, 0, 6, 0x7f.toByte(), 0xff.toByte())))

        val result = clues(host(vendor = "Nanoleaf"), meshcop)

        result.filterIsInstance<Clue.Model>() shouldContainExactly listOf(Clue.Model("SQFX01", Source.PROTOCOL))
    }

    @Test
    fun a_host_without_records_yields_nothing() {
        clues(host()).shouldBeEmpty()
    }
}

private const val HOST_IP = "10.0.0.7"

private fun host(vendor: String? = null, mac: String? = null) = Host(ip = HOST_IP, name = null, model = null, vendor = vendor, services = null, mac = mac)

private fun clues(host: Host, vararg services: ServiceInfo): List<Clue> =
    MdnsClues(FakeMdns(*services), AppleCodes(DeviceModelCodes.load(DeviceModelCodes.resource))).clues(host)
