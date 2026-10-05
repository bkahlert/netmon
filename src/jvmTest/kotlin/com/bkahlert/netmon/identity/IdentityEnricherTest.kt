package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.invoke
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IdentityEnricherTest {

    @Test
    fun resolves_name_model_vendor_and_kind_from_the_sources() {
        val enricher = IdentityEnricher(
            IdentityResolver(),
            sources = listOf(
                ClueSource { listOf(Clue.Name("LEDVANCE-Sideboard-TV", Source.ROUTER)) },
                ClueSource { listOf(Clue.Vendor("Tuya", Source.OUI), Clue.DeviceKind(Kind.SOCKET, Source.OUI)) },
            ),
        )

        val result = enricher.enrich(scanned(vendor = "Tuya Smart"))

        result should {
            it?.name shouldBe "LEDVANCE-Sideboard-TV"
            it?.vendor shouldBe "Ledvance"
            it?.kind shouldBe Kind.LAMP
        }
    }

    @Test
    fun a_placeholder_name_is_dropped_but_still_read_for_tokens() {
        val enricher = IdentityEnricher(IdentityResolver(), sources = listOf(ClueSource { listOf(Clue.Name("espressif", Source.ROUTER)) }))

        val result = enricher.enrich(scanned())

        result should {
            it?.name shouldBe null
            it?.kind shouldBe Kind.CIRCUIT_BOARD
        }
    }

    @Test
    fun nmaps_reverse_name_ranks_below_the_mdns_host_and_above_the_router() {
        val enricher = IdentityEnricher(
            IdentityResolver(),
            sources = listOf(ClueSource { listOf(Clue.Name("PC-192-168-16-12", Source.ROUTER), Clue.Name("pi-hole.local.", Source.MDNS_HOST)) }),
        )

        enricher.enrich(scanned(name = "Bellonda-PiHole.fritz.box."))?.name shouldBe "pi-hole"
        IdentityEnricher(IdentityResolver(), sources = listOf(ClueSource { listOf(Clue.Name("PC-192-168-16-12", Source.ROUTER)) }))
            .enrich(scanned(name = "Bellonda-PiHole.fritz.box."))?.name shouldBe "Bellonda-PiHole.fritz.box"
    }

    @Test
    fun the_raw_vendor_is_replaced_by_the_resolved_one() {
        val enricher = IdentityEnricher(IdentityResolver(), sources = listOf(OuiClues()))

        enricher.enrich(scanned(vendor = "Raspberry Pi Foundation", mac = "b8:27:eb:66:2e:c2"))?.vendor shouldBe "Raspberry Pi"
    }

    @Test
    fun sources_and_fallbacks_read_the_scanned_name_vendor_and_mac() {
        val seen = mutableListOf<Host>()
        val enricher = IdentityEnricher(
            IdentityResolver(),
            sources = listOf(ClueSource { seen += it; emptyList() }),
            fallbacks = listOf(ClueSource { seen += it; emptyList() }),
        )

        enricher.enrich(scanned(name = "stilgar.fritz.box.", vendor = "Raspberry Pi Foundation", mac = "b8:27:eb:66:2e:c2"))

        seen.map { Triple(it.name, it.vendor, it.mac) } shouldBe List(2) { Triple("stilgar.fritz.box.", "Raspberry Pi Foundation", "b8:27:eb:66:2e:c2") }
    }

    @Test
    fun fallback_sources_run_only_without_a_model_clue() {
        var fallbackCalls = 0
        val fallback = ClueSource { fallbackCalls++; listOf(Clue.Model("iPad7,5", Source.APPLE_CODE)) }

        val withModel = IdentityEnricher(IdentityResolver(), sources = listOf(ClueSource { listOf(Clue.Model("T8400", Source.PROTOCOL)) }), fallbacks = listOf(fallback))
        val withoutModel = IdentityEnricher(IdentityResolver(), sources = emptyList(), fallbacks = listOf(fallback))

        withModel.enrich(scanned())?.model shouldBe "T8400"
        withoutModel.enrich(scanned())?.model shouldBe "iPad7,5"
        fallbackCalls shouldBe 1
    }

    @Test
    fun a_host_with_no_clues_is_generic() {
        IdentityEnricher(IdentityResolver(), sources = emptyList()).enrich(scanned())?.kind shouldBe Kind.GENERIC
    }
}

private fun scanned(name: String? = null, vendor: String? = null, mac: String? = "aa:bb:cc:00:11:22") =
    Host(name = name, model = null, vendor = vendor, services = null, mac = mac)
