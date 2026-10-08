package com.bkahlert.netmon.display.presentation

import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.display.support.fritz2.runTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test

class DeviceIconsResourceTest {

    @Test
    fun the_shipped_asset_has_a_symbol_for_every_kind_and_both_link_glyphs() = runTest {
        val icons = DeviceIcons.load(DeviceIcons.resource)

        Kind.entries.forEach { kind -> icons.kindSymbol(kind).shouldNotBeNull().shouldStartWith("<svg") }
        icons.symbol("mdi:ethernet").shouldNotBeNull()
        icons.symbol("mdi:wifi").shouldNotBeNull()
    }
}
