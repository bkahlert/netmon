package com.bkahlert.netmon

import com.bkahlert.netmon.mqtt.MqttPublisher
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MqttPublisherTest {

    @Test
    fun publish() {
        val publisher = MqttPublisher(
            host = "test.mosquitto.org",
            port = 1883,
            stringFormat = JsonFormat,
            serializer = Event.serializer(),
        )

        publisher.publish("test", Event.DOWN) shouldBe true
    }
}
