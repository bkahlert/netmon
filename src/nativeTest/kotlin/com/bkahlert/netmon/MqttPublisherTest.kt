package com.bkahlert.netmon

import com.bkahlert.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MqttPublisherTest {

    @Test
    fun publish() {
        val publisher = MqttPublisher(
            topic = "test",
            host = "test.mosquitto.org",
            port = 1883,
            stringFormat = JsonFormat,
            serializer = ScanEvent.serializer(),
        )

        publisher.publish(ScanEvent.DOWN) shouldBe MqttPublicationSuccess
    }
}
