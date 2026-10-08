package com.bkahlert.netmon.scanner.identity

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed
import com.bkahlert.netmon.contract.invoke
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IdentityPipelineTest {

    @Test
    fun mdns_model_wins_when_multiple_protocol_sources_report_a_model() {
        pipeline(
            mdns = { listOf(Clue.Model("mDNS model", Source.PROTOCOL)) },
            ssdp = { listOf(Clue.Model("SSDP model", Source.PROTOCOL)) },
        ).enrich(identityHost()).model shouldBe "mDNS model"
    }

    @Test
    fun each_field_takes_the_first_clue_in_its_source_order() {
        val clues = listOf(
            Clue.Name("unicorn", Source.ROUTER),
            Clue.Name("Example-Pi", Source.PROTOCOL),
            Clue.Model("AirPort4", Source.APPLE_CODE),
            Clue.Model("Raspberry Pi Zero 2 W Rev 1.0", Source.PROTOCOL),
            Clue.Vendor("Raspberry Pi Ltd", Source.PROTOCOL),
            Clue.Vendor("Raspberry Pi", Source.OUI),
            Clue.DeviceKind(Kind.ROUTER, Source.ROUTER),
            Clue.DeviceKind(Kind.CIRCUIT_BOARD, Source.PROTOCOL),
        )

        val result = pipeline(router = { clues }, mdns = { clues }).enrich(identityHost())

        result should {
            it.name shouldBe "Example-Pi"
            it.model shouldBe "Raspberry Pi Zero 2 W Rev 1.0"
            it.vendor shouldBe "Raspberry Pi"
            it.kind shouldBe Kind.CIRCUIT_BOARD
        }
    }

    @Test
    fun within_one_source_the_first_clue_wins() {
        pipeline(
            mdns = {
                listOf(
                    Clue.Model("Fire TV Stick 4K", Source.PROTOCOL),
                    Clue.Model("AirReceiver", Source.PROTOCOL),
                )
            },
        ).enrich(identityHost()).model shouldBe "Fire TV Stick 4K"
    }

    @Test
    fun the_user_name_outranks_every_other_name() {
        pipeline(
            router = { listOf(Clue.Name("sam-wi-fi", Source.ROUTER)) },
            mdns = {
                listOf(
                    Clue.Name("Sam", Source.PROTOCOL),
                    Clue.Name("Sam (Wi-Fi)", Source.USER),
                )
            },
        ).enrich(identityHost()).name shouldBe "Sam (Wi-Fi)"
    }

    @Test
    fun a_filled_field_is_kept() {
        val host = identityHost(model = "iPad8,3")

        pipeline(mdns = { listOf(Clue.Model("Other", Source.PROTOCOL)) }).enrich(host).model shouldBe "iPad8,3"
    }

    @Test
    fun link_speed_and_mac_come_from_the_router_only() {
        val clues = listOf(
            Clue.Attachment(Link.WIFI, Source.PROTOCOL),
            Clue.Speed(LinkSpeed(72), Source.ROUTER),
            Clue.Mac("aa:bb:cc:dd:ee:ff", Source.ROUTER),
        )

        val result = pipeline(router = { clues }, mdns = { clues }).enrich(identityHost())

        result should {
            it.link shouldBe null
            it.speed shouldBe LinkSpeed(72)
            it.mac shouldBe "aa:bb:cc:dd:ee:ff"
        }
    }

    @Test
    fun a_host_without_kind_clues_is_generic() {
        pipeline().enrich(identityHost()).kind shouldBe Kind.GENERIC
    }

    @Test
    fun fallback_sources_run_only_without_a_model_clue() {
        var fallbackCalls = 0
        val fallback: (Host) -> List<Clue> = {
            fallbackCalls++
            listOf(Clue.Model("iPad7,5", Source.APPLE_CODE))
        }

        val withModel = pipeline(mdns = { listOf(Clue.Model("T8400", Source.PROTOCOL)) }, fallback = fallback)
        val withoutModel = pipeline(fallback = fallback)

        withModel.enrich(identityHost()).model shouldBe "T8400"
        withoutModel.enrich(identityHost()).model shouldBe "iPad7,5"
        fallbackCalls shouldBe 1
    }

    @Test
    fun placeholders_are_dropped_but_still_read_for_tokens() {
        pipeline(router = { listOf(Clue.Name("espressif", Source.ROUTER)) })
            .enrich(identityHost()).let {
                it.name shouldBe null
                it.kind shouldBe Kind.CIRCUIT_BOARD
            }
    }

    @Test
    fun name_cleanup_and_precedence_preserve_the_best_available_name() {
        pipeline(
            router = {
                listOf(
                    Clue.Name("PC-192-0-2-12", Source.ROUTER),
                    Clue.Name("pi-hole.local.", Source.MDNS_HOST),
                )
            },
        ).enrich(identityHost(name = "Example-PiHole.fritz.box.")).name shouldBe "pi-hole"

        pipeline(router = { listOf(Clue.Name("PC-192-0-2-12", Source.ROUTER)) })
            .enrich(identityHost(name = "Example-PiHole.fritz.box.")).name shouldBe "Example-PiHole.fritz.box"
    }

    @Test
    fun name_tokens_can_refine_vendor_and_kind() {
        pipeline(
            router = { listOf(Clue.Name("LEDVANCE-Hallway-TV", Source.ROUTER)) },
            oui = { listOf(Clue.Vendor("Tuya", Source.OUI), Clue.DeviceKind(Kind.SOCKET, Source.OUI)) },
        ).enrich(identityHost(vendor = "Tuya Smart")).let {
            it.name shouldBe "LEDVANCE-Hallway-TV"
            it.vendor shouldBe "Ledvance"
            it.kind shouldBe Kind.LAMP
        }
    }

    @Test
    fun sources_receive_the_original_scanned_host() {
        val seen = mutableListOf<Host>()
        val capture: (Host) -> List<Clue> = { seen += it; emptyList() }
        val host = identityHost(name = "example-pi.fritz.box.", vendor = "Raspberry Pi Foundation", mac = "b8:00:00:00:00:01")

        pipeline(router = capture, mdns = capture, ssdp = capture, oui = capture).enrich(host)

        seen shouldBe List(4) { host }
    }

    @Test
    fun the_resolved_vendor_replaces_the_raw_vendor() {
        pipeline(oui = { listOf(Clue.Vendor("Raspberry Pi", Source.OUI)) })
            .enrich(identityHost(vendor = "Raspberry Pi Foundation", mac = "b8:00:00:00:00:01"))
            .vendor shouldBe "Raspberry Pi"
    }
}

private fun identityHost(
    name: String? = null,
    model: String? = null,
    vendor: String? = null,
    mac: String? = null,
) = Host(name = name, model = model, vendor = vendor, services = null, mac = mac, kind = null, link = null, speed = null)

private fun pipeline(
    router: (Host) -> List<Clue> = { emptyList() },
    mdns: (Host) -> List<Clue> = { emptyList() },
    ssdp: (Host) -> List<Clue> = { emptyList() },
    oui: (Host) -> List<Clue> = { emptyList() },
    fallback: (Host) -> List<Clue> = { emptyList() },
) = IdentityEnricher(
    routerClues = ClueSource(router),
    mdnsClues = ClueSource(mdns),
    ssdpClues = ClueSource(ssdp),
    ouiClues = ClueSource(oui),
    lockdownClues = ClueSource(fallback),
)
