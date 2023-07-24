package com.bkahlert.netmon

import com.bkahlert.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MqttPublisherTest {

    @Test
    fun publish() {
        val publisher = MqttPublisher(
            host = "test.mosquitto.org",
            port = 1883,
            stringFormat = JsonFormat,
            serializer = ScanEvent.serializer(),
        )

        publisher.publish("test", ScanEvent.DOWN) shouldBe true
    }
}
