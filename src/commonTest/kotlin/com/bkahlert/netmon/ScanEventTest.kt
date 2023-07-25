package com.bkahlert.netmon

import com.bkahlert.serialization.JsonFormat
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class ScanEventTest {

    @Test
    fun to_json_up() {
        JsonFormat.encodeToString(ScanEvent.UP) shouldEqualJson ScanEvent.UP_STRING
        JsonFormat.encodeToString(ScanEvent.serializer(), ScanEvent.UP) shouldEqualJson ScanEvent.UP_STRING
    }

    @Test
    fun to_json_down() {
        JsonFormat.encodeToString(ScanEvent.DOWN) shouldEqualJson ScanEvent.DOWN_STRING
        JsonFormat.encodeToString(ScanEvent.serializer(), ScanEvent.DOWN) shouldEqualJson ScanEvent.DOWN_STRING
    }

    @Test
    fun from_json_up() {
        JsonFormat.decodeFromString<ScanEvent>(ScanEvent.UP_STRING) shouldBe ScanEvent.UP
        JsonFormat.decodeFromString(ScanEvent.serializer(), ScanEvent.UP_STRING) shouldBe ScanEvent.UP
    }

    @Test
    fun from_json_down() {
        JsonFormat.decodeFromString<ScanEvent>(ScanEvent.DOWN_STRING) shouldBe ScanEvent.DOWN
        JsonFormat.decodeFromString(ScanEvent.serializer(), ScanEvent.DOWN_STRING) shouldBe ScanEvent.DOWN
    }
}

val ScanEvent.Companion.UP: ScanEvent get() = ScanEvent.HostUpEvent("foo.bar", Host.UP)
val ScanEvent.Companion.UP_STRING: String
    get() =
        """
            {
              "event": "host-up",
              "source": "foo.bar",
              "host": ${Host.UP_STRING}
            }
        """.trimIndent()


val ScanEvent.Companion.DOWN: ScanEvent get() = ScanEvent.HostDownEvent("foo.bar", Host.DOWN)
val ScanEvent.Companion.DOWN_STRING: String
    get() =
        """
            {
              "event": "host-down",
              "source": "foo.bar",
              "host": ${Host.DOWN_STRING}
            }
        """.trimIndent()
