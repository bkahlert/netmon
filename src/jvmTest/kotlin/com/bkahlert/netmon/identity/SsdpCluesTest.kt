package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.invoke
import com.bkahlert.netmon.mdns.FakeMdns
import com.bkahlert.netmon.mdns.MdnsLookup
import com.bkahlert.netmon.mdns.service
import com.bkahlert.netmon.ssdp.DeviceDescription
import com.bkahlert.netmon.ssdp.SsdpLookup
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SsdpCluesTest {

    @Test
    fun a_description_gives_model_number_vendor_and_kind() {
        val nas = DeviceDescription("Vault", "Ugreen Group Limited", "UGREEN", "DXP4800 Plus", "urn:schemas-upnp-org:device:NAS:1", "uuid:1")

        val result = SsdpClues(lookup(nas), FakeMdns()).clues(Host())

        result shouldContainExactly listOf(
            Clue.Model("DXP4800 Plus", Source.PROTOCOL),
            Clue.Vendor("Ugreen", Source.PROTOCOL),
            Clue.DeviceKind(Kind.STORAGE, Source.PROTOCOL),
        )
    }

    @Test
    fun a_blank_or_bare_version_model_number_falls_back_to_the_model_name() = runTest {
        forAll(
            row(DeviceDescription("x", "LG Electronics.", "LG TV", "1.0", "urn:schemas-upnp-org:device:MediaRenderer:1", null), "LG TV"),
            row(DeviceDescription("x", "SoftMedia Inc.", "AirReceiver", "01", "urn:schemas-upnp-org:device:MediaRenderer:1", null), "AirReceiver"),
            row(DeviceDescription("x", "Sonos, Inc.", "Sonos One SL", "S22", "urn:schemas-upnp-org:device:ZonePlayer:1", null), "S22"),
            row(DeviceDescription("x", "Signify", "Philips hue bridge 2015", null, "urn:schemas-upnp-org:device:Basic:1", null), "Philips hue bridge 2015"),
            row(DeviceDescription("x", "Signify", "Philips hue bridge 2015", " ", "urn:schemas-upnp-org:device:Basic:1", null), "Philips hue bridge 2015"),
        ) { description, expected ->
            SsdpClues(lookup(description), FakeMdns()).clues(Host()) shouldContain Clue.Model(expected, Source.PROTOCOL)
        }
    }

    @Test
    fun device_types_map_to_kinds() = runTest {
        forAll(
            row("urn:schemas-upnp-org:device:InternetGatewayDevice:1", Kind.ROUTER),
            row("urn:schemas-upnp-org:device:fritzbox:1", Kind.ROUTER),
            row("urn:lge:device:tv:1", Kind.TELEVISION),
            row("urn:schemas-upnp-org:device:ZonePlayer:1", Kind.SPEAKER),
            row("urn:schemas-upnp-org:device:Printer:1", Kind.PRINTER),
            row("urn:schemas-upnp-org:device:NAS:1", Kind.STORAGE),
        ) { deviceType, kind ->
            SsdpClues(lookup(DeviceDescription("x", null, "m", null, deviceType, null)), FakeMdns()).clues(Host()) shouldContain Clue.DeviceKind(kind, Source.PROTOCOL)
        }
        SsdpClues(lookup(DeviceDescription("x", null, "m", null, "urn:schemas-upnp-org:device:Basic:1", null)), FakeMdns()).clues(Host())
            .filterIsInstance<Clue.DeviceKind>().shouldBeEmpty()
    }

    @Test
    fun an_airplay_receiver_app_gives_no_vendor() {
        val receiver = DeviceDescription("x", "SoftMedia Inc.", "AirReceiver", "01", "urn:schemas-upnp-org:device:MediaRenderer:1", null)

        val result = SsdpClues(lookup(receiver), emulator()).clues(Host())

        result shouldContainExactly listOf(Clue.Model("AirReceiver", Source.PROTOCOL))
    }

    @Test
    fun no_description_yields_nothing() {
        SsdpClues(lookup(null), FakeMdns()).clues(Host()).shouldBeEmpty()
    }
}

private fun lookup(description: DeviceDescription?) = object : SsdpLookup {
    override fun device(ip: IP): DeviceDescription? = description
}

private fun emulator(): MdnsLookup =
    FakeMdns(service("airplay", "Receiver", "receiver.local.", 7000, Host().ip.toString(), "model" to "AppleTV3,1", "rmodel" to "AirReceiver3,1"))
