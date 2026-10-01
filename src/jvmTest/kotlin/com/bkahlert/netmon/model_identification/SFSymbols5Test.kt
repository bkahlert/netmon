package com.bkahlert.netmon.model_identification

import com.bkahlert.kommons.SystemLocations
import com.bkahlert.kommons.test.open
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAllValues
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test

class SFSymbols5Test {

    @Test
    fun entries() {
        SFSymbols5.entries should {
            it.size shouldBeGreaterThan 100
        }
    }

    @Test
    fun keys() {
        SFSymbols5.keys.shouldContainAll("doc", "doc.fill", "doc.circle", "doc.circle.fill", "macbook.gen1")
    }

    @Test
    fun get_existing() {
        SFSymbols5["doc"] should {
            it.shouldNotBeNull()
            it.trimEnd() should { svg ->
                svg shouldStartWith "<svg "
                svg shouldEndWith "</svg>"
            }
        }
    }

    @Test
    fun get_missing() {
        SFSymbols5["_missing_"].shouldBeNull()
    }

    @Test
    fun unpatched_for_custom_instantiations() {
        val custom = SFSymbols5()
        custom["doc"] should {
            it.shouldNotBeNull()
            it.shouldStartWith("""<svg width="29" height="29" viewBox="0 0 29 29"""")
        }
    }

    @Test
    fun width_removed_by_default() {
        SFSymbols5.filterValues { it.isNotEmpty() }.forAllValues {
            it.substringBefore("\n").shouldNotContain("width=")
        }
    }

    @Test
    fun height_removed_by_default() {
        SFSymbols5.filterValues { it.isNotEmpty() }.forAllValues {
            it.substringBefore("\n").shouldNotContain("height=")
        }
    }

    @Test
    fun black_replaced_with_currentColor_by_default() {
        SFSymbols5.filterValues { it.isNotEmpty() }.forAllValues {
            it shouldNotContain "black"
            it shouldContain "currentColor"
        }
    }

    @Test
    fun non_fill_variants_fixed() {
        listOf(
            "applewatch",
            "desktopcomputer", "display",
            "ipad.gen1", "ipad.gen2", "ipad", "iphone.gen1", "iphone.gen2", "iphone.gen3", "iphone", "ipodtouch",
            "macbook.gen1", "macbook.gen2", "macbook",
            "visionpro",
            "tv",
        ).forAll { name ->
            SFSymbols5[name].shouldContain("""fill-opacity="0"""")
        }
    }

    @Test
    fun fill_opacity_85_removed_by_default() {
        SFSymbols5.filterValues { it.isNotEmpty() }.forAllValues {
            it shouldNotContain """fill-opacity="0.85""""
        }
    }


    @Test
    fun overview() {
        shouldNotThrowAny {
            SFSymbols5.dumpTo(SystemLocations.Work.resolve("build/reports"))
        }.open()
    }
}
