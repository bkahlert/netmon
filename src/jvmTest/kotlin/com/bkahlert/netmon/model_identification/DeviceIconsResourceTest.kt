package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.Kind
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test

class DeviceIconsResourceTest {

    @Test
    fun the_shipped_asset_has_a_symbol_for_every_kind_and_both_link_glyphs() {
        val icons = DeviceIcons.load(DeviceIcons.resource)

        Kind.entries.forEach { kind -> icons.kindSymbol(kind).shouldNotBeNull().shouldStartWith("<svg") }
        icons.symbol("mdi:ethernet").shouldNotBeNull()
        icons.symbol("mdi:wifi").shouldNotBeNull()
    }
}
