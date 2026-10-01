package com.bkahlert.netmon.mqtt

import kotlin.time.Clock
import com.bkahlert.netmon.DOWN
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class MqttPublisherTest {

    @Test
    fun publish() = runTest {
        val publisher = MqttPublisher(
            host = "test.mosquitto.org",
            port = 1883,
            stringFormat = JsonFormat,
            serializer = Event.serializer(),
        )
        publisher.publish("test", Event.DOWN) shouldBe true
    }

    @Test
    fun to_string() = runTest {
        val publisher = MqttPublisher(
            host = "test.mosquitto.org",
            port = 1883,
            stringFormat = JsonFormat,
            serializer = Event.serializer(),
        )
        publisher.toString() shouldMatch Regex("MqttPublisher\\(url=test.mosquitto.org:1883, status=MqttConnAck.*\\)")
    }
}
