package com.bkahlert.netmon

import kotlin.time.Clock
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.time.Instant
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

    @Test
    fun last_seen_round_trips_as_epoch_seconds() {
        val host = Host(ip = IP.of("10.0.0.1"), status = Status.UP, since = 1690159731L.epoch, lastSeen = 1690159800L.epoch)

        val json = JsonFormat.encodeToString(Host.serializer(), host)

        json shouldContain "\"lastSeen\": 1690159800"
        JsonFormat.decodeFromString(Host.serializer(), json) shouldBe host
    }

    @Test
    fun mac_round_trips() {
        val host = Host(ip = IP.of("10.0.0.1"), status = Status.UP, mac = "dc:a6:32:a5:ba:b6")

        val json = JsonFormat.encodeToString(Host.serializer(), host)

        json shouldContain "\"mac\": \"dc:a6:32:a5:ba:b6\""
        JsonFormat.decodeFromString(Host.serializer(), json) shouldBe host
    }

    @Test
    fun kind_link_and_speed_are_written_when_set_and_omitted_when_null() {
        val host = Host(ip = IP.of("10.0.0.1"), kind = Kind.TELEVISION, link = Link.WIFI, speed = LinkSpeed(866))

        val json = JsonFormat.encodeToString(Host.serializer(), host)

        json shouldContain "\"kind\": \"Television\""
        json shouldContain "\"link\": \"wifi\""
        json shouldContain "\"speed\": 866"
        JsonFormat.encodeToString(Host.serializer(), Host(ip = IP.of("10.0.0.1"))).shouldNotContain("\"kind\"")
    }

    @Test
    fun an_unknown_link_token_reads_as_null() {
        val result = JsonFormat.decodeFromString(Host.serializer(), """{"ip":"10.0.0.1","link":"fiber","kind":"Hoverboard"}""")

        result.link shouldBe null
        result.kind shouldBe Kind.GENERIC
    }

    /**
     * Regression test for when [Host.since] wasn't serialized,
     * likely because of its default parameter `if (status == Status.UP) Clock.System.now() else null`.
     */
    @Test
    fun regression() {
        (0..100).map {
            val host = Host(ip = IP.of("10.0.0.1"), name = null, status = Status.UP, since = Clock.System.now())
            val json = JsonFormat.encodeToString(Host.serializer(), host)
            json.shouldContain("\"since\":")
            json.shouldNotContain("\"since\": null")
        }
    }
}

operator fun Host.Companion.invoke(
    ip: String = "10.0.0.1",
    name: String? = "foo",
    status: Status? = Status.UP,
    since: Instant? = null,
    model: String? = "FooPro6,1",
    vendor: String? = "ACME",
    services: Set<String>? = setOf("smb", "airplay"),
    lastSeen: Instant? = null,
    mac: String? = null,
    kind: Kind? = null,
    link: Link? = null,
    speed: LinkSpeed? = null,
) = Host(
    ip = IP.of(ip),
    name = name,
    status = status,
    since = since,
    model = model,
    vendor = vendor,
    services = services,
    lastSeen = lastSeen,
    mac = mac,
    kind = kind,
    link = link,
    speed = speed,
)

inline val Int.epoch get() = toLong().epoch
inline val Long.epoch get() = Instant.fromEpochSeconds(this)

val Host.Companion.UP: Host get() = Host(IP.of("10.0.0.1"), name = "foo.bar", status = Status.UP, since = 1690159731L.epoch)
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


val Host.Companion.DOWN: Host get() = Host(IP.of("10.0.0.1"))
val Host.Companion.DOWN_STRING: String
    get() =
        """
            {
                "ip": "10.0.0.1"
            }
        """.trimIndent()
