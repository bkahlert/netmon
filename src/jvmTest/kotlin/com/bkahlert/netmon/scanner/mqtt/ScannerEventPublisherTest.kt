package com.bkahlert.netmon.scanner.mqtt

import com.bkahlert.netmon.scanner.support.test.AbstractIntegrationTest
import com.bkahlert.netmon.contract.Cidr
import com.bkahlert.netmon.contract.Event
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.scanner.support.test.LogMessage
import com.bkahlert.netmon.contract.Status
import com.bkahlert.netmon.contract.epoch
import com.bkahlert.netmon.scanner.scan.ScanResult
import io.kotest.inspectors.forAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class ScannerEventPublisherTest : AbstractIntegrationTest() {

    @Test
    fun publishes_the_existing_event_shapes_and_topics() {
        val published = mutableListOf<Pair<String, Event>>()
        val publisher = ScannerEventPublisher(
            publisher = Publisher<Event> { topic, event ->
                published += topic to event
                true
            },
            scanTopic = "scan/en0",
            hostTopic = "host/en0",
        )
        val upHost = Host(ip = IP.of("10.0.0.1"), status = Status.UP)
        val downHost = Host(ip = IP.of("10.0.0.2"), status = Status.DOWN)
        val scan = ScanResult(
            `interface` = "en0",
            cidr = Cidr.parse("10.0.0.0/24"),
            hosts = listOf(upHost),
            timestamp = 200.epoch,
        )

        publisher.publishScan(scan)
        publisher.publishChange(upHost)
        publisher.publishChange(downHost)

        published shouldBe listOf(
            "scan/en0" to Event.ScanEvent(Event.ScanEvent.Type.COMPLETED, scan.hosts, scan.timestamp),
            "host/en0" to Event.HostEvent(Event.HostEvent.Type.UP, upHost),
            "host/en0" to Event.HostEvent(Event.HostEvent.Type.DOWN, downHost),
        )
    }

    @Test
    fun failed_scan_publications_are_logged_without_throwing() {
        val logMessages = runUntilLogged(
            kClass = ScannerEventPublisherLogProbe::class,
            "scan",
            predicate = { it.contains("Failed publishing scan") },
        )

        logMessages.forAny { message ->
            message.level shouldBe LogMessage.Level.ERROR
            message.message shouldContain "Failed publishing scan"
        }
    }

    @Test
    fun failed_host_publications_are_logged_without_throwing() {
        val logMessages = runUntilLogged(
            kClass = ScannerEventPublisherLogProbe::class,
            "host",
            predicate = { it.contains("Failed publishing host") },
        )

        logMessages.forAny { message ->
            message.level shouldBe LogMessage.Level.ERROR
            message.message shouldContain "Failed publishing host"
        }
    }
}

object ScannerEventPublisherLogProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val publisher = ScannerEventPublisher(
            publisher = Publisher<Event> { _, _ -> false },
            scanTopic = "scan/en0",
            hostTopic = "host/en0",
        )
        when (args.single()) {
            "scan" -> publisher.publishScan(
                ScanResult(
                    `interface` = "en0",
                    cidr = Cidr.parse("10.0.0.0/24"),
                    hosts = emptyList(),
                    timestamp = 200.epoch,
                ),
            )
            "host" -> publisher.publishChange(Host(ip = IP.of("10.0.0.1"), status = Status.UP))
        }
    }
}
