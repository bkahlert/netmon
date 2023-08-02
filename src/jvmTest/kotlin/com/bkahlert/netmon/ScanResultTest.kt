package com.bkahlert.netmon

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.test.Test

class ScanResultTest {

    val `interface` = "en0"
    val cidr = Cidr("10.0.0.0/24")
    val old = ScanResult(
        `interface` = `interface`,
        cidr = cidr,
        timestamp = Instant.fromEpochSeconds(100),
        hosts = listOf(
            Host(IP("10.0.0.1"), "host1", Status.UP, Instant.fromEpochSeconds(100), "Mac14,13", "Apple", listOf("smb", "airplay")),
            Host(IP("10.0.0.2"), "host2", Status.UP, Instant.fromEpochSeconds(100)),
            Host(IP("10.0.0.3"), "host3", Status.DOWN, null),
            Host(IP("10.0.0.4"), "host4", Status.DOWN, Instant.fromEpochSeconds(100)),
        ),
    )
    val current = ScanResult(
        `interface` = `interface`,
        cidr = cidr,
        timestamp = Instant.fromEpochSeconds(200),
        hosts = listOf(
            Host(IP("10.0.0.1"), "host1", Status.UP, model = "Mac14,13", vendor = "Apple", services = listOf("smb", "airplay")),
            Host(IP("10.0.0.3"), null, Status.UP),
        ),
    )

    val new = ScanResult(
        `interface` = `interface`,
        cidr = cidr,
        timestamp = Instant.fromEpochSeconds(200),
        hosts = listOf(
            Host(IP("10.0.0.1"), "host1", Status.UP, Instant.fromEpochSeconds(100), "Mac14,13", "Apple", listOf("smb", "airplay")),
            Host(IP("10.0.0.2"), "host2", Status.DOWN, Instant.fromEpochSeconds(200)),
            Host(IP("10.0.0.3"), null, Status.UP, Instant.fromEpochSeconds(200)),
            Host(IP("10.0.0.4"), "host4", Status.DOWN, Instant.fromEpochSeconds(100)),
        ),
    )

    @Test
    fun merge() {
        val changes = mutableListOf<Host>()
        old.merge(current) { changes.add(it) } shouldBe new
        changes.shouldContainExactly(
            Host(IP("10.0.0.2"), "host2", Status.DOWN, Instant.fromEpochSeconds(200)),
            Host(IP("10.0.0.3"), null, Status.UP, Instant.fromEpochSeconds(200)),
        )
    }
}
