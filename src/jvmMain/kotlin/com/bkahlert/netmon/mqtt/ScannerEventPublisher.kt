package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.Event
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.scanner.ScanResult

class ScannerEventPublisher(
    private val publisher: Publisher<Event>,
    private val scanTopic: String,
    private val hostTopic: String,
) {
    private val logger by SLF4J

    fun publishScan(scan: ScanResult) {
        val event = Event.ScanEvent(Event.ScanEvent.Type.COMPLETED, scan.hosts, scan.timestamp)
        if (publisher.publish(scanTopic, event)) {
            logger.info(
                "Scan {}:{} with {} host(s) completed and published to {}: {}",
                scan.`interface`,
                scan.cidr,
                scan.hosts.size,
                scanTopic,
                scan.hosts.joinToString(limit = 4) { it.ip.toString() },
            )
        } else {
            logger.error("Failed publishing scan to {}", scanTopic)
        }
    }

    fun publishChange(host: Host) {
        val event = Event.HostEvent(
            type = if (host.status == Status.DOWN) Event.HostEvent.Type.DOWN else Event.HostEvent.Type.UP,
            host = host,
        )
        if (!publisher.publish(hostTopic, event)) {
            logger.error("Failed publishing host change to {}", hostTopic)
        }
    }
}
