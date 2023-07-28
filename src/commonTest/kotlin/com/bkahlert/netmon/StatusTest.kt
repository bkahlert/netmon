package com.bkahlert.netmon

import io.kotest.matchers.shouldBe
import io.ktor.http.quote
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class StatusTest {

    @Test
    fun to_json() {
        JsonFormat.encodeToString(Status.UP as Status) shouldBe "up".quote()
        JsonFormat.encodeToString(Status.DOWN as Status) shouldBe "down".quote()
        JsonFormat.encodeToString(Status.UNKNOWN("foo") as Status) shouldBe "unknown-foo".quote()
    }

    @Test
    fun from_json() {
        JsonFormat.decodeFromString<Status>("up".quote()) shouldBe Status.UP
        JsonFormat.decodeFromString<Status>("down".quote()) shouldBe Status.DOWN
        JsonFormat.decodeFromString<Status>("unknown-foo".quote()) shouldBe Status.UNKNOWN("foo")
        JsonFormat.decodeFromString<Status>("bar".quote()) shouldBe Status.UNKNOWN("bar")
    }
}
