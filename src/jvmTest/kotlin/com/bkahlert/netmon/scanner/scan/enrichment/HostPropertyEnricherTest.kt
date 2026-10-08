package com.bkahlert.netmon.scanner.scan.enrichment

import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.invoke
import com.bkahlert.netmon.scanner.discovery.mdns.FakeMdns
import com.bkahlert.netmon.scanner.discovery.mdns.service
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HostPropertyEnricherTest {

    @Test
    fun host_services_enricher_uses_mdns_lookup() {
        val host = Host(ip = IP.of("10.0.0.1"))
        val enricher = HostServicesEnricher(FakeMdns(service("googlecast", "Living room")))

        enricher.enrich(host) shouldBe host.copy(services = setOf("googlecast"))
    }

    @Test
    fun copy_sets_the_mac() {
        val host = Host(mac = null)

        val result = with(HostPropertyEnricher) { host.copy(Host::mac, "aa:bb:cc:dd:ee:ff") }

        result shouldBe host.copy(mac = "aa:bb:cc:dd:ee:ff")
    }
}
