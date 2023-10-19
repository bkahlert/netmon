package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.test.Test

class ScanResultTest {
    // TODO test host name change

    val old = scan(
        timestamp = 100.epoch,
        Host(IP("10.0.0.1"), "host1", Status.UP, 100.epoch, "Mac14,13", "Apple", setOf("smb", "airplay")),
        Host(IP("10.0.0.2"), "host2", Status.UP, 100.epoch),
        Host(IP("10.0.0.3"), "host3", Status.DOWN, null),
        Host(IP("10.0.0.4"), "host4", Status.DOWN, 100.epoch),
    )
    val current = scan(
        timestamp = 200.epoch,
        Host(IP("10.0.0.1"), "host1", Status.UP, model = "Mac14,13", vendor = "Apple", services = setOf("smb", "airplay")),
        Host(IP("10.0.0.3"), null, Status.UP),
    )

    val new = scan(
        timestamp = 200.epoch,
        Host(IP("10.0.0.1"), "host1", Status.UP, 100.epoch, "Mac14,13", "Apple", setOf("smb", "airplay")),
        Host(IP("10.0.0.2"), "host2", Status.DOWN, 200.epoch),
        Host(IP("10.0.0.3"), null, Status.UP, 200.epoch),
        Host(IP("10.0.0.4"), "host4", Status.DOWN, 100.epoch),
    )

    @Test
    fun merge() {
        val changes = mutableListOf<Host>()
        old.merge(current) { changes.add(it) } shouldBe new
        changes.shouldContainExactly(
            Host(IP("10.0.0.2"), "host2", Status.DOWN, 200.epoch),
            Host(IP("10.0.0.3"), null, Status.UP, 200.epoch),
        )
    }

    @Test
    fun update_hostname() {
        val changes = mutableListOf<Host>()
        scan(
            timestamp = 100.epoch,
            Host(IP("10.0.0.1"), "foo", Status.UP, 100.epoch),
        ).merge(
            scan(
                timestamp = 200.epoch,
                Host(IP("10.0.0.1"), "bar", Status.UP),
            )
        ) { changes.add(it) } shouldBe scan(
            timestamp = 200.epoch,
            Host(IP("10.0.0.1"), "bar", Status.UP, 100.epoch),
        )
        changes.shouldContainExactly(
            Host(IP("10.0.0.1"), "bar", Status.UP, 100.epoch),
        )
    }
}

private val Int.epoch
    get() = Instant.fromEpochSeconds(toLong())

private fun scan(
    timestamp: Instant,
    vararg hosts: Host,
) = ScanResult(
    `interface` = "en0",
    cidr = Cidr("10.0.0.0/24"),
    timestamp = timestamp,
    hosts = hosts.toList(),
)
