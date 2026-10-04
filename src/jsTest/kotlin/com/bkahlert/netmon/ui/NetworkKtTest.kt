package com.bkahlert.netmon.ui

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Event.ScanEvent
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.fritz2.runTest
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import dev.fritz2.core.RootStore
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NetworkKtTest {

    @Test
    fun a_cards_since_text_follows_the_clock_it_is_given() = runTest {
        val now = Clock.System.now()
        val clock = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now - 30.seconds)), job = job)
        val container = rendered { hosts(store, clock = clock) }
        container.textOnce("since 30s") shouldContain "since 30s"

        clock.value = now + 90.seconds

        container.textOnce("since 2m") shouldContain "since 2m"
        container.remove()
    }

    @Test
    fun a_card_unchanged_for_a_minute_follows_the_slow_clock() = runTest {
        val now = Clock.System.now()
        val fast = MutableStateFlow(now)
        val slow = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now - 5.minutes)), job = job)
        val container = rendered { hosts(store, clock = fast, slowClock = slow) }
        container.textOnce("since 5m") shouldContain "since 5m"

        fast.value = now + 60.seconds
        nextFrames(3)

        container.textContent.orEmpty() shouldContain "since 5m"

        slow.value = now + 60.seconds

        container.textOnce("since 6m") shouldContain "since 6m"
        container.remove()
    }

    @Test
    fun a_card_hands_over_to_the_slow_clock_once_a_minute_old() = runTest {
        val now = Clock.System.now()
        val fast = MutableStateFlow(now)
        val slow = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now - 30.seconds)), job = job)
        val container = rendered { hosts(store, clock = fast, slowClock = slow) }
        container.textOnce("since 30s") shouldContain "since 30s"

        fast.value = now + 40.seconds
        container.textOnce("since 1m") shouldContain "since 1m"
        fast.value = now + 100.seconds
        nextFrames(3)

        container.textContent.orEmpty() shouldContain "since 1m"

        slow.value = now + 150.seconds

        container.textOnce("since 3m") shouldContain "since 3m"
        container.remove()
    }

    @Test
    fun a_scan_writes_the_size_of_each_section_for_the_css() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds), host(2, now - 3.hours), host(3, now - 5.hours)), job = job)

        val container = rendered { scan(source, events) }

        container.awaited({ hostIps(".hosts--stable") }) { it.size == 2 } shouldContainExactly listOf("192.168.1.2", "192.168.1.3")
        container.hostIps(".hosts--unstable") shouldContainExactly listOf("192.168.1.1")
        container.sectionSizes() shouldBe ("1" to "2")
        container.remove()
    }

    @Test
    fun a_scan_updates_the_sizes_when_a_host_settles() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds), host(2, now - 40.seconds)), job = job)
        val container = rendered { scan(source, events) }
        container.awaited({ sectionSizes() }) { it == ("2" to "0") } shouldBe ("2" to "0")

        events.update(scan(now, host(1, now - 30.seconds), host(2, now - 2.hours)))

        container.awaited({ sectionSizes() }) { it == ("1" to "1") } shouldBe ("1" to "1")
        container.remove()
    }

    @Test
    fun a_scan_with_hosts_in_one_section_only_has_no_divider() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds)), job = job)

        val container = rendered { scan(source, events) }
        container.awaited({ hostIps(".hosts--unstable") }) { it.isNotEmpty() } shouldContainExactly listOf("192.168.1.1")
        nextFrames(3)

        container.querySelector(".divider-xs") shouldBe null
        container.remove()
    }

    @Test
    fun a_host_writes_the_length_of_each_line_it_fits() = runTest {
        val now = Clock.System.now()
        val named = Host(ip = IP.of("192.168.1.1"), name = "printer.local.", vendor = "Acme", status = Status.UP, since = now)
        val store = RootStore(listOf(named), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.awaited({ fitLengths() }) { it.size == 3 } shouldContainExactly listOf("7", "4", "11")
        container.remove()
    }

    @Test
    fun a_host_without_name_and_vendor_fits_the_placeholders() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.awaited({ fitLengths() }) { it.size == 2 } shouldContainExactly listOf("16", "11")
        container.remove()
    }

    @Test
    fun a_host_fits_its_model_by_the_longest_word() = runTest {
        val codes = DeviceModelCodes(models = mapOf("Mac14,8" to DeviceModelCodes.Model(description = "Mac Studio", symbol = null)))
        DeviceModelCodes.set(codes)
        try {
            val now = Clock.System.now()
            val studio = Host(ip = IP.of("192.168.1.1"), model = "Mac14,8", status = Status.UP, since = now)
            val store = RootStore(listOf(studio), job = job)

            val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

            container.awaited({ fitLengths() }) { it.size == 4 } shouldContainExactly listOf("6", "10", "16", "11")
            container.remove()
        } finally {
            DeviceModelCodes.set(DeviceModelCodes())
        }
    }

    @Test
    fun a_card_carries_no_zoom() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds), host(2, now - 3.hours)), job = job)

        val container = rendered { scan(source, events) }
        container.awaited({ hostIps(".hosts--stable") }) { it.isNotEmpty() } shouldContainExactly listOf("192.168.1.2")
        nextFrames(5)

        container.querySelectorAll("[style*=zoom], [data-zoomed], [data-zooming]").length shouldBe 0
        container.remove()
    }
}

private val source = EventSource("test", "en0", Cidr.parse("192.168.1.0/24"))

private fun host(index: Int, since: Instant) = Host(ip = IP.of("192.168.1.$index"), status = Status.UP, since = since)

private fun scan(now: Instant, vararg hosts: Host) = ScanEvent(ScanEvent.Type.COMPLETED, hosts.toList(), now)

private fun HTMLElement.sectionSizes(): Pair<String, String> {
    val area = querySelector(".scan__hosts") as? HTMLElement ?: return "" to ""
    return area.style.getPropertyValue("--unstable").trim() to area.style.getPropertyValue("--stable").trim()
}

private fun HTMLElement.hostIps(section: String): List<String> =
    querySelectorAll("$section .font-mono").asList().map { it.textContent.orEmpty() }

private fun HTMLElement.fitLengths(): List<String> =
    querySelectorAll(".fit").asList().map { (it as HTMLElement).style.getPropertyValue("--len").trim() }
