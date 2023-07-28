package com.bkahlert.netmon

import com.bkahlert.kommons.time.Now
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlin.test.Test

class HostTest {

    @Test
    fun to_json_up() {
        JsonFormat.encodeToString(Host.UP) shouldBe Host.UP_STRING
        JsonFormat.encodeToString(Host.serializer(), Host.UP) shouldBe Host.UP_STRING
    }

    @Test
    fun to_json_down() {
        JsonFormat.encodeToString(Host.DOWN) shouldBe Host.DOWN_STRING
        JsonFormat.encodeToString(Host.serializer(), Host.DOWN) shouldBe Host.DOWN_STRING
    }

    @Test
    fun from_json_up() {
        JsonFormat.decodeFromString<Host>(Host.UP_STRING) shouldBe Host.UP
        JsonFormat.decodeFromString(Host.serializer(), Host.UP_STRING) shouldBe Host.UP
    }

    @Test
    fun from_json_down() {
        JsonFormat.decodeFromString<Host>(Host.DOWN_STRING) shouldBe Host.DOWN
        JsonFormat.decodeFromString(Host.serializer(), Host.DOWN_STRING) shouldBe Host.DOWN
    }

    /**
     * Regression test for when [Host.since] wasn't serialized,
     * likely because of its default parameter `if (status == Status.UP) Now else null`.
     */
    @Test
    fun regression() {
        (0..100).map {
            val host = Host(ip = IP("10.0.0.1"), name = null, status = Status.UP, since = Now)
            val json = JsonFormat.encodeToString(Host.serializer(), host)
            json.shouldContain("\"since\":")
            json.shouldNotContain("\"since\": null")
        }
    }

}

val Host.Companion.UP: Host get() = Host(IP("10.0.0.1"), name = "foo.bar", status = Status.UP, since = Instant.fromEpochSeconds(1690159731L))
val Host.Companion.UP_STRING: String
    get() =
        """
            {
                "ip": "10.0.0.1",
                "name": "foo.bar",
                "status": "up",
                "since": 1690159731
            }
        """.trimIndent()


val Host.Companion.DOWN: Host get() = Host(IP("10.0.0.1"))
val Host.Companion.DOWN_STRING: String
    get() =
        """
            {
                "ip": "10.0.0.1"
            }
        """.trimIndent()
