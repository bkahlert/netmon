package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.enrichment.LockdownProbe
import com.bkahlert.netmon.invoke
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LockdownCluesTest {

    @Test
    fun an_answer_is_an_apple_code_with_vendor_and_kind() {
        val result = LockdownClues(probe { "iPad7,5" }, appleCodes).clues(Host(vendor = null, mac = "02:aa:bb:cc:00:15"))

        result shouldContainExactly listOf(
            Clue.Model("iPad7,5", Source.APPLE_CODE),
            Clue.Vendor("Apple", Source.APPLE_CODE),
            Clue.DeviceKind(Kind.TABLET, Source.APPLE_CODE),
        )
    }

    @Test
    fun a_host_with_another_vendor_is_not_probed() {
        var probed = false

        val result = LockdownClues(probe { probed = true; "iPad7,5" }, appleCodes).clues(Host(vendor = "Raspberry Pi Trading"))

        result.shouldBeEmpty()
        probed shouldBe false
    }

    @Test
    fun no_answer_yields_nothing() {
        LockdownClues(probe { null }, appleCodes).clues(Host(vendor = "Apple Inc.")).shouldBeEmpty()
    }
}

private val appleCodes = AppleCodes(loadModelCatalog())

private fun probe(answer: () -> String?) = object : LockdownProbe.Lookup {
    override fun model(ip: IP, mac: String?): String? = answer()
}
