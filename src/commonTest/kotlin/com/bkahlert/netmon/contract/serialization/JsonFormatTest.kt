package com.bkahlert.netmon.contract.serialization

import com.bkahlert.netmon.contract.Event
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.Kind
import com.bkahlert.netmon.contract.Link
import com.bkahlert.netmon.contract.LinkSpeed
import com.bkahlert.netmon.contract.Status
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.time.Instant

class JsonFormatTest {

    @Test
    fun wire_fields_and_tokens_are_stable() {
        val timestamp = Instant.fromEpochSeconds(123)
        val host = Host(
            ip = IP.of("192.0.2.42"),
            name = "router",
            status = Status.UP,
            since = timestamp,
            model = "Router1,1",
            vendor = "Example",
            services = setOf("http"),
            lastSeen = Instant.fromEpochSeconds(456),
            mac = "aa:bb:cc:dd:ee:ff",
            kind = Kind.TABLET,
            link = Link.WIFI,
            speed = LinkSpeed(866),
        )
        val hostJson = JsonFormat.encodeToString(host)
        val hostFields = JsonFormat.parseToJsonElement(hostJson).jsonObject

        hostFields.keys shouldBe setOf("ip", "name", "status", "since", "model", "vendor", "services", "lastSeen", "mac", "kind", "link", "speed")
        hostFields.getValue("status").jsonPrimitive.content shouldBe "up"
        hostFields.getValue("since").jsonPrimitive.long shouldBe 123
        hostFields.getValue("lastSeen").jsonPrimitive.long shouldBe 456
        hostFields.getValue("kind").jsonPrimitive.content shouldBe "Tablet"
        hostFields.getValue("link").jsonPrimitive.content shouldBe "wifi"
        hostFields.getValue("speed").jsonPrimitive.int shouldBe 866

        val minimalHostJson = JsonFormat.encodeToString(Host(ip = IP.of("192.0.2.42")))
        val minimalHostFields = JsonFormat.parseToJsonElement(minimalHostJson).jsonObject
        minimalHostFields.keys shouldBe setOf("ip")

        val futureHostJson = """{"ip":"192.0.2.42","kind":"Hoverboard","futureField":true}"""
        val futureHost = JsonFormat.decodeFromString<Host>(futureHostJson)
        futureHost.kind shouldBe Kind.GENERIC

        val scanJson = JsonFormat.encodeToString<Event>(
            Event.ScanEvent(Event.ScanEvent.Type.COMPLETED, listOf(host), timestamp),
        )
        val scanFields = JsonFormat.parseToJsonElement(scanJson).jsonObject
        scanFields.keys shouldBe setOf("event", "type", "hosts", "timestamp")
        scanFields.getValue("event").jsonPrimitive.content shouldBe "scan"
        scanFields.getValue("type").jsonPrimitive.content shouldBe "completed"
        scanFields.getValue("timestamp").jsonPrimitive.long shouldBe 123

        val hostEventJson = JsonFormat.encodeToString<Event>(Event.HostEvent(Event.HostEvent.Type.UP, host))
        val hostEventFields = JsonFormat.parseToJsonElement(hostEventJson).jsonObject
        hostEventFields.getValue("event").jsonPrimitive.content shouldBe "host"
        hostEventFields.getValue("type").jsonPrimitive.content shouldBe "up"
    }
}
