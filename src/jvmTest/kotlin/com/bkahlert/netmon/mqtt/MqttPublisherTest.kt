package com.bkahlert.netmon.mqtt

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
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

    @Test
    fun connect_options_reconnect_by_themselves_within_the_displays_dated_threshold() {
        val options = MqttPublisher.connectOptions()

        options should {
            it.isCleanSession shouldBe true
            it.isAutomaticReconnect shouldBe true
            it.maxReconnectDelay shouldBe 30_000
        }
    }

    @Test
    fun close_disconnects_then_closes_the_client_once() {
        val operations = mutableListOf<String>()
        val publisher = MqttPublisher(
            host = "broker.local",
            port = 1883,
            serializer = String.serializer(),
            client = FakeMqttClient(operations),
        )

        publisher.close()
        publisher.close()

        operations shouldBe listOf("disconnect", "close")
    }

    @Test
    fun close_still_closes_the_client_when_disconnect_fails() {
        val operations = mutableListOf<String>()
        val disconnectFailure = MqttException(1)
        val publisher = MqttPublisher(
            host = "broker.local",
            port = 1883,
            serializer = String.serializer(),
            client = FakeMqttClient(operations, disconnectFailure),
        )

        val failure = shouldThrow<MqttException> { publisher.close() }

        failure shouldBe disconnectFailure
        operations shouldBe listOf("disconnect", "close")
    }
}

private class FakeMqttClient(
    private val operations: MutableList<String>,
    private val disconnectFailure: MqttException? = null,
) : MqttClient("tcp://localhost:1883", "netmon-test", MemoryPersistence()) {
    override fun isConnected(): Boolean = true

    override fun disconnect() {
        operations += "disconnect"
        disconnectFailure?.let { throw it }
    }

    override fun close() {
        operations += "close"
    }
}
