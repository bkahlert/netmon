package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.epoch
import com.bkahlert.netmon.invoke
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class RestartFloorTest {

    @Test
    fun floor_is_the_scan_time_minus_the_uptime() {
        val time = TestTimeSource()
        val floor = RestartFloor(time.markNow())

        time += 70.seconds

        floor.at(1000.epoch) shouldBe 930.epoch
    }

    @Test
    fun floor_follows_a_wall_clock_jump_so_a_missed_host_stays_up() {
        val time = TestTimeSource()
        val floor = RestartFloor(time.markNow())
        val recorded = Host(status = Status.UP, since = 40.epoch, lastSeen = 40.epoch)
        time += 70.seconds

        val merged = scanAt(40.epoch, recorded).merge(
            currentResult = scanAt(7240.epoch),
            downAfter = 3.minutes,
            notBefore = floor.at(7240.epoch),
        )

        merged.scan.hosts.single().status shouldBe Status.UP
    }

    @Test
    fun a_wall_clock_floor_turns_a_missed_host_down_after_a_wall_clock_jump() {
        val recorded = Host(status = Status.UP, since = 40.epoch, lastSeen = 40.epoch)

        val merged = scanAt(40.epoch, recorded).merge(
            currentResult = scanAt(7240.epoch),
            downAfter = 3.minutes,
            notBefore = 10.epoch,
        )

        merged.scan.hosts.single().status shouldBe Status.DOWN
    }
}

private fun scanAt(timestamp: Instant, vararg hosts: Host) = ScanResult(
    `interface` = "en0",
    cidr = Cidr.parse("10.0.0.0/24"),
    timestamp = timestamp,
    hosts = hosts.toList(),
)
