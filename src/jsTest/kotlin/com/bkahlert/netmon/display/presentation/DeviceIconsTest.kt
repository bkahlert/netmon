package com.bkahlert.netmon.display.presentation

import com.bkahlert.netmon.contract.Kind
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
        ICONS.specificSymbol(vendor = "Sonos", model = "One SL", name = "Living Room") shouldBe "<svg>one</svg>"
        ICONS.specificSymbol(vendor = "Sonos", model = "Play:5", name = null) shouldBe "<svg>sonos</svg>"
        ICONS.specificSymbol(vendor = "Nintendo", model = "Switch OLED", name = null) shouldBe "<svg>switch</svg>"
        ICONS.specificSymbol(vendor = "Nintendo", model = null, name = "Console").shouldBeNull()
        ICONS.specificSymbol(vendor = null, model = "One SL", name = null).shouldBeNull()
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
        DeviceIcons.Matcher(vendor = "^Sonos$", model = "^One", symbol = "mdi:speaker-wireless"),
        DeviceIcons.Matcher(vendor = "^Sonos$", symbol = "simple-icons:sonos"),
        DeviceIcons.Matcher(vendor = "^Nintendo$", model = "^Switch", symbol = "mdi:nintendo-switch"),
    ),
    symbols = mapOf("mdi:television" to "<svg>tv</svg>", "mdi:speaker-wireless" to "<svg>one</svg>", "simple-icons:sonos" to "<svg>sonos</svg>", "mdi:nintendo-switch" to "<svg>switch</svg>", "mdi:wifi" to "<svg>wifi</svg>"),
)
