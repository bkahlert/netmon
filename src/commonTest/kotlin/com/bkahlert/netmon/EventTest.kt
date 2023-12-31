package com.bkahlert.netmon

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class EventTest {

    @Test
    fun to_json_up() {
        JsonFormat.encodeToString(Event.HOST_UP) shouldBe Event.HOST_UP_STRING
        JsonFormat.encodeToString(Event.serializer(), Event.HOST_UP) shouldBe Event.HOST_UP_STRING
    }

    @Test
    fun to_json_down() {
        JsonFormat.encodeToString(Event.DOWN) shouldBe Event.DOWN_STRING
        JsonFormat.encodeToString(Event.serializer(), Event.DOWN) shouldBe Event.DOWN_STRING
    }

    @Test
    fun from_json_up() {
        JsonFormat.decodeFromString<Event>(Event.HOST_UP_STRING) shouldBe Event.HOST_UP
        JsonFormat.decodeFromString(Event.serializer(), Event.HOST_UP_STRING) shouldBe Event.HOST_UP
    }

    @Test
    fun from_json_down() {
        JsonFormat.decodeFromString<Event>(Event.DOWN_STRING) shouldBe Event.DOWN
        JsonFormat.decodeFromString(Event.serializer(), Event.DOWN_STRING) shouldBe Event.DOWN
    }
}

val Event.Companion.HOST_UP: Event get() = Event.HostEvent(Event.HostEvent.Type.UP, Host.UP)
val Event.Companion.HOST_UP_STRING: String
    get() =
        """
            {
                "event": "host",
                "type": "up",
                "host": ${Host.UP_STRING.prependIndent(" ".repeat(16)).trimStart()}
            }
        """.trimIndent()


val Event.Companion.DOWN: Event get() = Event.HostEvent(Event.HostEvent.Type.DOWN, Host.DOWN)
val Event.Companion.DOWN_STRING: String
    get() =
        """
            {
                "event": "host",
                "type": "down",
                "host": ${Host.DOWN_STRING.prependIndent(" ".repeat(16)).trimStart()}
            }
        """.trimIndent()
