package com.bkahlert.netmon.mqtt

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MqttPublisherTest {

    @Test
    fun url_is_websocket_for_the_brokers_websocket_ports_and_tcp_otherwise() = runTest {
        forAll(
            row(1883, null, "tcp://broker.local:1883"),
            row(8080, null, "ws://broker.local:8080"),
            row(8081, "mqtt", "ws://broker.local:8081/mqtt"),
        ) { port, path, expected ->
            MqttPublisher.url("broker.local", port, path) shouldBe expected
        }
    }
}
