package com.bkahlert.netmon.model_identification

import com.bkahlert.netmon.Kind
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DeviceIconsTest {

    @Test
    fun a_kind_resolves_to_its_symbols_svg() {
        ICONS.kindSymbol(Kind.TELEVISION) shouldBe "<svg>tv</svg>"
        ICONS.kindSymbol(Kind.ROBOT).shouldBeNull()
    }

    @Test
    fun the_first_matching_specific_rule_wins_and_every_given_field_must_match() {
        ICONS.specificSymbol(vendor = "Nanoleaf", model = "NL42", name = "Shapes 6632") shouldBe "<svg>shapes</svg>"
        ICONS.specificSymbol(vendor = "Nanoleaf", model = "SQFX01", name = null) shouldBe "<svg>nanoleaf</svg>"
        ICONS.specificSymbol(vendor = "Amazon", model = "Fire TV Stick 4K", name = null) shouldBe "<svg>firetv</svg>"
        ICONS.specificSymbol(vendor = "Amazon", model = null, name = "Echo").shouldBeNull()
        ICONS.specificSymbol(vendor = null, model = "NL42", name = null).shouldBeNull()
    }

    @Test
    fun a_symbol_is_found_by_id() {
        ICONS.symbol("mdi:wifi") shouldBe "<svg>wifi</svg>"
        ICONS.symbol("mdi:lan").shouldBeNull()
    }
}

private val ICONS = DeviceIcons(
    kinds = mapOf("Television" to "mdi:television"),
    specific = listOf(
        DeviceIcons.Matcher(vendor = "^Amazon$", model = "Fire TV", symbol = "cbi:firetv"),
        DeviceIcons.Matcher(vendor = "^Nanoleaf$", model = "^NL42", symbol = "cbi:nanoleaf-shapes"),
        DeviceIcons.Matcher(vendor = "^Nanoleaf$", symbol = "cbi:nanoleaf"),
    ),
    symbols = mapOf("mdi:television" to "<svg>tv</svg>", "cbi:firetv" to "<svg>firetv</svg>", "cbi:nanoleaf-shapes" to "<svg>shapes</svg>", "cbi:nanoleaf" to "<svg>nanoleaf</svg>", "mdi:wifi" to "<svg>wifi</svg>"),
)
