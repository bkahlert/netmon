package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.DOWN
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MqttPublisherIntegrationTest {

    @Test
    fun publishes_a_retained_event_over_tcp() {
        val publisher = MqttPublisher(host = broker.host, port = broker.firstMappedPort, stringFormat = JsonFormat, serializer = Event.serializer())

        val result = publisher.publish(topic, Event.DOWN)

        result shouldBe true
        publisher.toString() shouldBe "MqttPublisher(url=tcp://${broker.host}:${broker.firstMappedPort}, connected=true)"
        val received = retainedMessage()
        received.isRetained shouldBe true
        received.payload.decodeToString() shouldBe JsonFormat.encodeToString(Event.serializer(), Event.DOWN)
    }

    @Test
    fun keeps_the_interrupt_of_a_publish_waiting_for_its_acknowledgement() {
        val publisher = MqttPublisher(host = broker.host, port = broker.firstMappedPort, stringFormat = JsonFormat, serializer = Event.serializer())
        broker.dockerClient.pauseContainerCmd(broker.containerId).exec()
        try {
            val outcome = CompletableFuture<Pair<Boolean, Boolean>>()
            val publishing = thread {
                val published = publisher.publish(topic, Event.DOWN)
                outcome.complete(published to Thread.currentThread().isInterrupted)
            }

            publishing.interrupt()
            val result = outcome.get(10, TimeUnit.SECONDS)

            result shouldBe (false to true)
        } finally {
            broker.dockerClient.unpauseContainerCmd(broker.containerId).exec()
        }
    }

    private val topic = "test"

    private val broker: GenericContainer<*> = GenericContainer<Nothing>(DockerImageName.parse("eclipse-mosquitto:1.5")).withExposedPorts(1883)

    private fun retainedMessage(): MqttMessage {
        val subscriber = MqttClient("tcp://${broker.host}:${broker.firstMappedPort}", UUID.randomUUID().toString(), MemoryPersistence())
        try {
            subscriber.connect(MqttConnectOptions().apply { isCleanSession = true })
            val arrived = CountDownLatch(1)
            var message: MqttMessage? = null
            subscriber.subscribe(topic, 1) { _, received ->
                message = received
                arrived.countDown()
            }
            check(arrived.await(10, TimeUnit.SECONDS)) { "No retained message arrived on $topic" }
            return checkNotNull(message)
        } finally {
            subscriber.disconnect()
            subscriber.close()
        }
    }

    @BeforeTest
    fun setUp() {
        broker.start()
    }

    @AfterTest
    fun tearDown() {
        broker.stop()
    }
}
