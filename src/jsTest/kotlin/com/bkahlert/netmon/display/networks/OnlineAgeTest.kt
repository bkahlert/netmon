package com.bkahlert.netmon.display.networks

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.bkahlert.netmon.contract.DOWN
import com.bkahlert.netmon.contract.IP
import com.bkahlert.netmon.contract.Status
import com.bkahlert.netmon.contract.Host
import com.bkahlert.netmon.contract.UP

class OnlineAgeTest {

    @Test
    fun the_age_steps_up_at_each_bound() {
        val times = listOf(
            4.minutes + 59.seconds, 5.minutes,
            19.minutes + 59.seconds, 20.minutes,
            59.minutes + 59.seconds, 1.hours,
            11.hours + 59.minutes + 59.seconds, 12.hours,
            23.hours + 59.minutes + 59.seconds, 24.hours,
        )

        val tokens = times.map { OnlineAge.of(since = SINCE, now = SINCE + it).token }

        tokens shouldBe listOf("5m", "20m", "20m", "1h", "1h", "12h", "12h", "24h", "24h", "older")
    }

    @Test
    fun a_since_ahead_of_now_is_the_newest_age() {
        val result = OnlineAge.of(since = SINCE, now = SINCE - 30.seconds)

        result shouldBe OnlineAge.FIVE_MINUTES
    }

    @Test
    fun an_up_host_is_as_old_as_its_since() {
        val host = Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = SINCE)

        val result = host.onlineAge(now = SINCE + 30.minutes)

        result shouldBe OnlineAge.ONE_HOUR
    }

    @Test
    fun a_down_host_has_no_age() {
        val host = Host(ip = IP.of("192.0.2.1"), status = Status.DOWN, since = SINCE)

        val result = host.onlineAge(now = SINCE + 30.minutes)

        result.shouldBeNull()
    }

    @Test
    fun a_host_of_unknown_status_has_no_age() {
        val host = Host(ip = IP.of("192.0.2.1"), status = Status.UNKNOWN("filtered"), since = SINCE)

        val result = host.onlineAge(now = SINCE + 30.minutes)

        result.shouldBeNull()
    }

    @Test
    fun an_up_host_without_since_has_no_age() {
        val host = Host(ip = IP.of("192.0.2.1"), status = Status.UP)

        val result = host.onlineAge(now = SINCE + 30.minutes)

        result.shouldBeNull()
    }
}

private val SINCE = Instant.fromEpochSeconds(1_800_000_000)
