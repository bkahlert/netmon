package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Kind
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class HapCategoriesTest {

    @Test
    fun maps_the_hap_accessory_categories_to_kinds() = runTest {
        forAll(
            row("2", Kind.HUB), row("5", Kind.LAMP), row("6", Kind.DOOR_LOCK), row("7", Kind.SOCKET), row("8", Kind.SOCKET),
            row("9", Kind.THERMOSTAT), row("10", Kind.SENSOR), row("14", Kind.SHUTTER), row("15", Kind.BUTTON),
            row("16", Kind.ROUTER), row("17", Kind.CAMERA), row("18", Kind.DOOR_BELL), row("19", Kind.AIR_PURIFIER),
            row("20", Kind.THERMOSTAT), row("21", Kind.AIR_CONDITIONER), row("24", Kind.SET_TOP_BOX), row("25", Kind.SPEAKER),
            row("26", Kind.SPEAKER), row("27", Kind.ROUTER), row("31", Kind.TELEVISION), row("33", Kind.ROUTER),
            row("34", Kind.SPEAKER), row("35", Kind.SET_TOP_BOX), row("36", Kind.SET_TOP_BOX),
        ) { category, expected ->
            HapCategories.kindOf(category) shouldBe expected
        }
    }

    @Test
    fun other_categories_and_garbage_map_to_nothing() = runTest {
        forAll(row("1"), row("3"), row("11"), row("99"), row("x"), row(null)) { category ->
            HapCategories.kindOf(category) shouldBe null
        }
    }
}
