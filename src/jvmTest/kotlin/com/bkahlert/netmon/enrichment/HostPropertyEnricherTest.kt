package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.invoke
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HostPropertyEnricherTest {

    @Test
    fun copy_sets_the_mac() {
        val host = Host(mac = null)

        val result = with(HostPropertyEnricher) { host.copy(Host::mac, "aa:bb:cc:dd:ee:ff") }

        result shouldBe host.copy(mac = "aa:bb:cc:dd:ee:ff")
    }
}
