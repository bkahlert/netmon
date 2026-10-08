package com.bkahlert.netmon.contract

import com.bkahlert.netmon.contract.serialization.JsonFormat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class LinkSpeedTest {

    @Test
    fun formats_megabits_below_a_gigabit_and_gigabits_with_one_decimal() = runTest {
        forAll(
            row(6, "6 Mbit/s"),
            row(72, "72 Mbit/s"),
            row(866, "866 Mbit/s"),
            row(1000, "1 Gbit/s"),
            row(1088, "1.1 Gbit/s"),
            row(1297, "1.3 Gbit/s"),
            row(2500, "2.5 Gbit/s"),
        ) { megabits, expected ->
            LinkSpeed(megabits).toString() shouldBe expected
        }
    }

    @Test
    fun serializes_as_the_megabit_number() {
        JsonFormat.encodeToString(LinkSpeed.serializer(), LinkSpeed(2500)) shouldBe "2500"
        JsonFormat.decodeFromString(LinkSpeed.serializer(), "72") shouldBe LinkSpeed(72)
    }

    @Test
    fun rejects_zero_and_negative_rates() {
        shouldThrow<IllegalArgumentException> { LinkSpeed(0) }
    }
}
