package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.epoch
import com.bkahlert.netmon.enrichment.Enricher
import com.bkahlert.netmon.mqtt.Publisher
import com.bkahlert.netmon.mqtt.ScannerEventPublisher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class NetmonScannerTest {

    @Test
    fun existing_state_runs_the_cycle_in_order() {
        val operations = mutableListOf<String>()
        val context = NetworkContext("en0", Cidr.parse("10.0.0.0/24"))
        val scannedHost = Host(ip = IP.of("10.0.0.2"), status = Status.UP)
        var savedScan: ScanResult? = null
        val state = object : ScanStateStore {
            override fun load(): ScanResult {
                operations += "load"
                return scanAt(100.epoch)
            }

            override fun save(scan: ScanResult) {
                operations += "save"
                savedScan = scan
            }
        }
        val publisher = ScannerEventPublisher(
            publisher = Publisher<Event> { topic, event ->
                operations += when (event) {
                    is Event.HostEvent -> "host publication"
                    is Event.ScanEvent -> "scan publication"
                }
                topic shouldBe if (event is Event.HostEvent) "host/topic" else "scan/topic"
                true
            },
            scanTopic = "scan/topic",
            hostTopic = "host/topic",
        )
        val scanner = NetmonScanner(
            context = context,
            scanner = NetworkScan { network, mode ->
                network shouldBe context.cidr
                mode shouldBe ScanMode.NORMAL
                operations += "normal scan"
                listOf(scannedHost)
            },
            enrichers = listOf(Enricher { host ->
                operations += "enrichment"
                host.copy(name = "enriched")
            }),
            state = state,
            clock = fixedClock(200.epoch),
            downAfter = 3.minutes,
            onScan = publisher::publishScan,
            onChange = publisher::publishChange,
        )

        scanner.scan()

        operations shouldBe listOf("load", "normal scan", "enrichment", "host publication", "scan publication", "save")
        savedScan shouldBe scanAt(
            200.epoch,
            Host(ip = IP.of("10.0.0.2"), name = "enriched", status = Status.UP, since = 200.epoch, lastSeen = 200.epoch),
        )
    }

    @Test
    fun a_failed_publish_does_not_skip_saving_current_state() {
        val operations = mutableListOf<String>()
        val context = NetworkContext("en0", Cidr.parse("10.0.0.0/24"))
        var savedScan: ScanResult? = null
        val state = object : ScanStateStore {
            override fun load(): ScanResult {
                operations += "load"
                return scanAt(100.epoch)
            }

            override fun save(scan: ScanResult) {
                operations += "save"
                savedScan = scan
            }
        }
        val publisher = ScannerEventPublisher(
            publisher = Publisher<Event> { _, event ->
                operations += if (event is Event.HostEvent) "host publication" else "scan publication"
                false
            },
            scanTopic = "scan/topic",
            hostTopic = "host/topic",
        )
        val scanner = NetmonScanner(
            context = context,
            scanner = NetworkScan { _, _ ->
                operations += "normal scan"
                listOf(Host(ip = IP.of("10.0.0.2"), status = Status.UP))
            },
            enrichers = emptyList(),
            state = state,
            clock = fixedClock(200.epoch),
            downAfter = 3.minutes,
            onScan = publisher::publishScan,
            onChange = publisher::publishChange,
        )

        scanner.scan()

        operations shouldBe listOf("load", "normal scan", "host publication", "scan publication", "save")
        savedScan?.timestamp shouldBe 200.epoch
    }

    @Test
    fun missing_state_runs_an_unenriched_initial_scan_before_the_normal_scan() {
        val operations = mutableListOf<String>()
        val context = NetworkContext("en0", Cidr.parse("10.0.0.0/24"))
        val initialHost = Host(ip = IP.of("10.0.0.1"), status = Status.UP)
        val normalHost = Host(ip = IP.of("10.0.0.2"), status = Status.UP)
        val modes = mutableListOf<ScanMode>()
        var savedScan: ScanResult? = null
        var enrichmentCount = 0
        val state = object : ScanStateStore {
            override fun load(): ScanResult? {
                operations += "load"
                return null
            }

            override fun save(scan: ScanResult) {
                operations += "save"
                savedScan = scan
            }
        }
        val publisher = ScannerEventPublisher(
            publisher = Publisher<Event> { _, event ->
                operations += if (event is Event.HostEvent) "host publication" else "scan publication"
                true
            },
            scanTopic = "scan/topic",
            hostTopic = "host/topic",
        )
        val scanner = NetmonScanner(
            context = context,
            scanner = NetworkScan { _, mode ->
                modes += mode
                operations += if (mode == ScanMode.INITIAL) "initial scan" else "normal scan"
                if (mode == ScanMode.INITIAL) listOf(initialHost) else listOf(normalHost)
            },
            enrichers = listOf(Enricher { host ->
                enrichmentCount++
                host.copy(name = "enriched")
            }),
            state = state,
            clock = sequenceClock(100.epoch, 200.epoch),
            downAfter = 3.minutes,
            onScan = publisher::publishScan,
            onChange = publisher::publishChange,
        )

        scanner.scan()

        modes shouldBe listOf(ScanMode.INITIAL, ScanMode.NORMAL)
        enrichmentCount shouldBe 1
        operations shouldBe listOf("load", "initial scan", "normal scan", "host publication", "scan publication", "save")
        val saved = checkNotNull(savedScan)
        saved.timestamp shouldBe 200.epoch
        saved.hosts shouldBe listOf(
            initialHost.copy(lastSeen = 100.epoch),
            normalHost.copy(name = "enriched", since = 200.epoch, lastSeen = 200.epoch),
        )
    }

    @Test
    fun a_scan_exception_prevents_publication_and_saving() {
        val operations = mutableListOf<String>()
        val context = NetworkContext("en0", Cidr.parse("10.0.0.0/24"))
        val state = object : ScanStateStore {
            override fun load(): ScanResult {
                operations += "load"
                return scanAt(100.epoch)
            }

            override fun save(scan: ScanResult) {
                operations += "save"
            }
        }
        val scanner = NetmonScanner(
            context = context,
            scanner = NetworkScan { _, _ ->
                operations += "normal scan"
                throw IllegalStateException("scan failed")
            },
            enrichers = emptyList(),
            state = state,
            clock = fixedClock(200.epoch),
            downAfter = 3.minutes,
            onScan = { operations += "scan publication" },
            onChange = { operations += "host publication" },
        )

        shouldThrow<IllegalStateException> { scanner.scan() }

        operations shouldBe listOf("load", "normal scan")
    }
}

private fun scanAt(timestamp: Instant, vararg hosts: Host) = ScanResult(
    `interface` = "en0",
    cidr = Cidr.parse("10.0.0.0/24"),
    timestamp = timestamp,
    hosts = hosts.toList(),
)

private fun fixedClock(timestamp: Instant): Clock = object : Clock {
    override fun now(): Instant = timestamp
}

private fun sequenceClock(vararg timestamps: Instant): Clock = object : Clock {
    private var index = 0

    override fun now(): Instant = timestamps[index++]
}
