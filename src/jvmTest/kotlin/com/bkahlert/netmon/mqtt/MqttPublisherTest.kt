package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.DOWN
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
}
