package com.bkahlert.netmon

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import org.junit.Test

class ScanResultTest {

    val network = Cidr("10.0.0.0/24")
    val old = ScanResult(
        network = network,
        timestamp = Instant.fromEpochSeconds(100),
        hosts = listOf(
            Host("10.0.0.1", "host1", Status.UP, Instant.fromEpochSeconds(100)),
            Host("10.0.0.2", "host2", Status.UP, Instant.fromEpochSeconds(100)),
            Host("10.0.0.3", "host3", Status.DOWN, Instant.fromEpochSeconds(100)),
            Host("10.0.0.4", "host4", Status.DOWN, Instant.fromEpochSeconds(100)),
        ),
    )
    val current = ScanResult(
        network = network,
        timestamp = Instant.fromEpochSeconds(200),
        hosts = listOf(
            Host("10.0.0.1", "host1", Status.UP),
            Host("10.0.0.3", null, Status.UP),
        ),
    )

    val new = ScanResult(
        network = network,
        timestamp = Instant.fromEpochSeconds(200),
        hosts = listOf(
            Host("10.0.0.1", "host1", Status.UP, Instant.fromEpochSeconds(100)),
            Host("10.0.0.2", "host2", Status.DOWN, Instant.fromEpochSeconds(200)),
            Host("10.0.0.3", null, Status.UP, Instant.fromEpochSeconds(200)),
            Host("10.0.0.4", "host4", Status.DOWN, Instant.fromEpochSeconds(100)),
        ),
    )

    @Test
    fun merge() {
        old.merge(current) shouldBe new
    }

    @Test
    fun diff() {
        old.diff(new).toList().shouldContainExactly(
            ScanEvent.HostDownEvent(Host("10.0.0.2", "host2", Status.DOWN, Instant.fromEpochSeconds(200))),
            ScanEvent.HostUpEvent(Host("10.0.0.3", null, Status.UP, Instant.fromEpochSeconds(200))),
        )
    }
}
