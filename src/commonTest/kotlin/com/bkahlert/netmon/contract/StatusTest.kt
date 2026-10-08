package com.bkahlert.netmon.contract

import com.bkahlert.netmon.contract.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class StatusTest {

    @Test
    fun to_json() {
        JsonFormat.encodeToString(Status.UP as Status) shouldBe "\"up\""
        JsonFormat.encodeToString(Status.DOWN as Status) shouldBe "\"down\""
        JsonFormat.encodeToString(Status.UNKNOWN("foo") as Status) shouldBe "\"unknown-foo\""
    }

    @Test
    fun from_json() {
        JsonFormat.decodeFromString<Status>("\"up\"") shouldBe Status.UP
        JsonFormat.decodeFromString<Status>("\"down\"") shouldBe Status.DOWN
        JsonFormat.decodeFromString<Status>("\"unknown-foo\"") shouldBe Status.UNKNOWN("foo")
        JsonFormat.decodeFromString<Status>("\"bar\"") shouldBe Status.UNKNOWN("bar")
    }
}
