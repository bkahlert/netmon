package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.contract.invoke
import com.bkahlert.netmon.scanner.discovery.mdns.FakeMdns
import com.bkahlert.netmon.scanner.discovery.mdns.ServiceInfo
import com.bkahlert.netmon.scanner.discovery.mdns.binaryProperty
import com.bkahlert.netmon.scanner.discovery.mdns.service
import com.bkahlert.netmon.scanner.discovery.mdns.withProperty
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MdnsCluesTest {

    @Test
    fun a_homekit_camera_gives_name_model_and_kind() {
        val result = clues(host(), service("hap", "IndoorCam 2K-0000", "Indoorcam.local.", 38757, HOST_IP, "md" to "T8400", "ci" to "17"))

        result shouldContainExactly listOf(
            Clue.Name("IndoorCam 2K-0000", Source.PROTOCOL),
            Clue.Name("Indoorcam.local.", Source.MDNS_HOST),
            Clue.Model("T8400", Source.PROTOCOL),
            Clue.DeviceKind(Kind.CAMERA, Source.PROTOCOL),
        )
    }

    @Test
    fun a_pi_gives_its_machine_as_model_and_rejects_the_airport_code() {
        val pi = host(vendor = "Raspberry Pi Foundation", mac = "b8:00:00:00:00:01")
        val result = clues(
            pi,
            service("device-info", "Example-Pi", "example-pi.local.", 0, HOST_IP, "model" to "AirPort4", "machine" to "Raspberry Pi Zero 2 W Rev 1.0"),
            service("workstation", "Example-Pi [b8:00:00:00:00:01]", "example-pi.local.", 9, HOST_IP),
        )

        result.filterIsInstance<Clue.Model>() shouldContainExactly listOf(Clue.Model("Raspberry Pi Zero 2 W Rev 1.0", Source.PROTOCOL))
        result shouldContain Clue.DeviceKind(Kind.CIRCUIT_BOARD, Source.PROTOCOL)
        result.filterIsInstance<Clue.Name>().first() shouldBe Clue.Name("Example-Pi", Source.PROTOCOL)
    }

    @Test
    fun an_unknown_oui_pi_with_an_airport_code_is_still_a_linux_host() {
        val result = clues(
            host(vendor = null, mac = "d4:00:00:00:00:02"),
            service("device-info", "Sample Board 32", "sample-board-32.local.", 0, HOST_IP, "model" to "AirPort5", "machine" to "Raspberry Pi Zero Rev 1.3"),
        )

        result.none { it.source == Source.APPLE_CODE } shouldBe true
    }

    @Test
    fun a_fire_tv_stick_with_an_airplay_receiver_app_is_a_set_top_box_named_by_whisperplay() {
        val result = clues(
            host(vendor = "Amazon Technologies", mac = "ec:00:00:00:00:03"),
            service("airplay", "AFTMM-36[AirPlay]", "192-0-2-36.local.", 7000, HOST_IP, "model" to "AppleTV3,1", "rmodel" to "AirReceiver3,1"),
            service("raop", "EC0000000003@AFTMM-36[AirPlay]", "192-0-2-36.local.", 7000, HOST_IP, "am" to "AppleTV3,1"),
            service("googlecast", "AFTMM-36[Cast]", "192-0-2-36.local.", 8010, HOST_IP, "md" to "AirReceiver", "fn" to "AFTMM-36[Cast]"),
            service("amzn-wplay", "amzn.dmgr:AAC1", "192-0-2-36.local.", 45458, HOST_IP, "n" to "Fire TV Stick 4K"),
        )

        result.filterIsInstance<Clue.Model>() shouldContainExactly listOf(Clue.Model("Fire TV Stick 4K", Source.PROTOCOL))
        result.filterIsInstance<Clue.DeviceKind>().first() shouldBe Clue.DeviceKind(Kind.SET_TOP_BOX, Source.PROTOCOL)
        result.filterIsInstance<Clue.Vendor>().shouldBeEmpty()
    }

    @Test
    fun an_airplay_receiver_app_on_an_unknown_oui_does_not_pass_for_apple() {
        val result = clues(
            host(vendor = null, mac = "ec:00:00:00:00:03"),
            service("airplay", "AFTMM-36[AirPlay]", "192-0-2-36.local.", 7000, HOST_IP, "model" to "AppleTV3,1", "rmodel" to "AirReceiver3,1"),
            service("raop", "EC0000000003@AFTMM-36[AirPlay]", "192-0-2-36.local.", 7000, HOST_IP, "am" to "AppleTV3,1"),
        )

        result.none { it.source == Source.APPLE_CODE } shouldBe true
    }

    @Test
    fun an_airplay_receiver_app_on_a_private_mac_does_not_pass_for_apple() {
        val result = clues(
            host(vendor = null, mac = "02:aa:bb:cc:00:15"),
            service("airplay", "Receiver", "receiver.local.", 7000, HOST_IP, "model" to "AppleTV3,1", "rmodel" to "AirReceiver3,1"),
        )

        result.none { it.source == Source.APPLE_CODE } shouldBe true
    }

    @Test
    fun a_homepod_is_an_apple_speaker_although_its_hap_record_is_a_sensor() {
        val result = clues(
            host(vendor = "Apple Inc.", mac = "f4:00:00:00:00:0e"),
            service("airplay", "Apple HomePod", "Apple-HomePod.local.", 7000, HOST_IP, "model" to "AudioAccessory5,1"),
            service("hap", "HomePodSensor 000001", "Apple-HomePod.local.", 52427, HOST_IP, "md" to "HomePod", "ci" to "10"),
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
            host(vendor = null, mac = "02:aa:bb:cc:00:15"),
            service("device-info", "Casey", server = null, port = 0, ip = HOST_IP, "model" to "iPad7,5"),
            service("companion-link", "Casey", "Casey.local.", 52412, HOST_IP, "rpBA" to "E0:00:00:00:00:1D"),
        )

        result shouldContain Clue.Model("iPad7,5", Source.APPLE_CODE)
        result shouldContain Clue.DeviceKind(Kind.TABLET, Source.APPLE_CODE)
        result shouldContain Clue.Name("Casey.local.", Source.MDNS_HOST)
    }

    @Test
    fun a_nanoleaf_panel_reads_model_from_its_api_record_and_vendor_from_thread() {
        val result = clues(
            host(vendor = "Nanoleaf", mac = "80:00:00:00:00:0f"),
            service("nanoleafapi", "Shapes 0001", "Shapes-0001.local.", 16021, HOST_IP, "md" to "NL42", "id" to "02:AA:BB:CC:00:1E"),
            service("meshcop", "Shapes 0001", "Shapes-0001.local.", 49154, HOST_IP, "vn" to "Nanoleaf", "mn" to "NL42", "nn" to "tado-0000"),
        )

        result shouldContain Clue.Name("Shapes 0001", Source.PROTOCOL)
        result.filterIsInstance<Clue.Model>().first() shouldBe Clue.Model("NL42", Source.PROTOCOL)
        result shouldContain Clue.Vendor("Nanoleaf", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.LAMP, Source.PROTOCOL)
    }

    @Test
    fun a_thread_border_router_is_a_hub_without_model_or_vendor() {
        val result = clues(
            host(vendor = "Espressif", mac = "84:00:00:00:00:10"),
            service("meshcop", "tado-IB0000000001", "tado-IB0000000001.local.", 49154, HOST_IP, "vn" to "OpenThread", "mn" to "BorderRouter"),
        )

        result.filterIsInstance<Clue.Model>().shouldBeEmpty()
        result.filterIsInstance<Clue.Vendor>().shouldBeEmpty()
        result shouldContain Clue.DeviceKind(Kind.HUB, Source.PROTOCOL)
    }

    @Test
    fun a_tv_takes_its_category_first_and_its_cast_model() {
        val result = clues(
            host(vendor = "LG Electronics", mac = "00:00:00:00:00:0a"),
            service("hap", "LG webOS TV 0000", "LGwebOSTV-2.local.", 38461, HOST_IP, "md" to "LG webOS TV", "ci" to "31"),
            service("googlecast", "OLED65C47LA.DEUQDJP-0000", "00000000.local.", 8009, HOST_IP, "md" to "OLED65C47LA.DEUQDJP", "fn" to "[LG] webOS TV OLED65C47LA"),
            service("airplay", "[LG] webOS TV OLED65C47LA", "LGwebOSTV-2.local.", 7000, HOST_IP, "model" to "OLED65C47LA.DEUQDJP", "manufacturer" to "LG"),
        )

        result.filterIsInstance<Clue.DeviceKind>().first() shouldBe Clue.DeviceKind(Kind.TELEVISION, Source.PROTOCOL)
        result.filterIsInstance<Clue.Model>().map { it.value } shouldContainInOrder listOf("LG webOS TV", "OLED65C47LA.DEUQDJP")
        result shouldContain Clue.Name("[LG] webOS TV OLED65C47LA", Source.PROTOCOL)
        result shouldContain Clue.Vendor("LG", Source.PROTOCOL)
    }

    @Test
    fun a_printer_reads_type_make_and_instance_name() {
        val result = clues(
            host(vendor = "HP", mac = "84:00:00:00:00:09"),
            service("ipp", "HP LaserJet M110w (000009)", "NPI000009.local.", 631, HOST_IP, "ty" to "HP LaserJet M109-M112", "usb_MFG" to "HP", "usb_MDL" to "HP LaserJet M109-M112"),
        )

        result shouldContain Clue.Name("HP LaserJet M110w (000009)", Source.PROTOCOL)
        result shouldContain Clue.Model("HP LaserJet M109-M112", Source.PROTOCOL)
        result shouldContain Clue.Vendor("HP", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.PRINTER, Source.PROTOCOL)
    }

    @Test
    fun a_sonos_speaker_keeps_its_marketing_model_and_the_name_after_the_at_sign() {
        val result = clues(
            host(vendor = "Sonos", mac = "48:00:00:00:00:11"),
            service("airplay", "Sonos One SL", "Sonos-480000000011.local.", 7000, HOST_IP, "model" to "One SL", "manufacturer" to "Sonos"),
            service("sonos", "RINCON_48000000001101400@Sonos One SL", "Sonos-480000000011.local.", 1443, HOST_IP),
        )

        result.filterIsInstance<Clue.Name>().map { it.value } shouldContainInOrder listOf("Sonos One SL", "Sonos One SL", "Sonos-480000000011.local.")
        result shouldContain Clue.Model("One SL", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.SPEAKER, Source.PROTOCOL)
    }

    @Test
    fun an_ewelink_plug_is_a_socket() {
        val result = clues(host(vendor = "Espressif"), service("ewelink", "eWeLink_1000000001", "48000000001C.local.", 8081, HOST_IP, "type" to "plug"))

        result shouldContain Clue.DeviceKind(Kind.SOCKET, Source.PROTOCOL)
    }

    @Test
    fun a_fire_tv_stick_announcing_matter_casting_is_a_set_top_box_named_by_its_dn() {
        val result = clues(
            host(vendor = "Amazon Technologies"),
            service("matterd", "A000000000000001", "EC000000001B.local.", 5550, HOST_IP, "DN" to "Fire TV Stick", "DT" to "35"),
        )

        result shouldContain Clue.Model("Fire TV Stick", Source.PROTOCOL)
        result shouldContain Clue.DeviceKind(Kind.SET_TOP_BOX, Source.PROTOCOL)
    }

    @Test
    fun binary_txt_values_are_left_alone() {
        val meshcop = service("meshcop", "LED 0001", "Example-Box-0001.local.", 49154, HOST_IP, "vn" to "Nanoleaf", "mn" to "SQFX01")
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
    MdnsClues(FakeMdns(*services), AppleCodes(loadModelCatalog())).clues(host)
