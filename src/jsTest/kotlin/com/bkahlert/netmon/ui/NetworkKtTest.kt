package com.bkahlert.netmon.ui

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Event.ScanEvent
import com.bkahlert.netmon.EventSource
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.Link
import com.bkahlert.netmon.LinkSpeed
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.fritz2.runTest
import com.bkahlert.netmon.model_identification.DeviceIcons
import com.bkahlert.netmon.model_identification.DeviceModelCodes
import dev.fritz2.core.RootStore
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
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
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now - 30.seconds)), job = job)
        val container = rendered { hosts(store, clock = clock) }
        container.textOnce("since 30s") shouldContain "since 30s"

        clock.value = now + 90.seconds

        container.textOnce("since 2m") shouldContain "since 2m"
        container.remove()
    }

    @Test
    fun a_cards_since_text_is_updated_in_place_not_rendered_anew() = runTest {
        val now = Clock.System.now()
        val clock = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now - 30.seconds)), job = job)
        val container = rendered { hosts(store, clock = clock) }
        container.textOnce("since 30s")
        val before = container.querySelector(".host__status")!!.nodes()

        clock.value = now + 5.seconds

        container.textOnce("since 35s")
        container.querySelector(".host__status")!!.nodes() shouldContainAll before
        container.remove()
    }

    @Test
    fun a_card_unchanged_for_a_minute_follows_the_slow_clock() = runTest {
        val now = Clock.System.now()
        val fast = MutableStateFlow(now)
        val slow = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now - 5.minutes)), job = job)
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
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now - 30.seconds)), job = job)
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
    fun a_card_carries_the_step_of_its_time_online() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now - 30.minutes)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.awaited({ ages() }) { it.isNotEmpty() } shouldContainExactly listOf("1h")
        container.remove()
    }

    @Test
    fun a_down_card_carries_no_step_of_time_online() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.DOWN, since = now - 30.minutes)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("since 30m")
        nextFrames(3)
        container.querySelector(".host")?.hasAttribute("data-age") shouldBe false
        container.remove()
    }

    @Test
    fun a_card_takes_the_next_step_of_time_online_on_a_tick_of_the_slow_clock() = runTest {
        val now = Clock.System.now()
        val fast = MutableStateFlow(now)
        val slow = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now - 5.minutes + 1.seconds)), job = job)
        val container = rendered { hosts(store, clock = fast, slowClock = slow) }
        container.awaited({ ages() }) { it.isNotEmpty() } shouldContainExactly listOf("5m")

        fast.value = now + 2.seconds
        nextFrames(3)
        container.ages() shouldContainExactly listOf("5m")

        slow.value = now + 2.seconds

        container.awaited({ ages() }) { it == listOf("20m") } shouldContainExactly listOf("20m")
        container.remove()
    }

    @Test
    fun a_scan_shows_hosts_of_any_age_in_one_collection() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds), host(2, now - 3.hours), host(3, now - 5.hours)), job = job)

        val container = rendered { scan(source, events) }

        container.awaited({ hostIps(".hosts") }) { it.size == 3 } shouldContainExactly listOf("192.0.2.1", "192.0.2.2", "192.0.2.3")
        container.querySelectorAll(".hosts").length shouldBe 1
        container.remove()
    }

    @Test
    fun a_scan_writes_the_number_of_cells_for_the_css() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds), host(2, now - 3.hours)), job = job)

        val container = rendered { scan(source, events) }

        container.awaited({ cells() }) { it == "3" } shouldBe "3"
        container.remove()
    }

    @Test
    fun a_scan_updates_the_number_of_cells_when_a_host_joins() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds)), job = job)
        val container = rendered { scan(source, events) }
        container.awaited({ cells() }) { it == "2" }

        events.update(scan(now, host(1, now - 30.seconds), host(2, now - 2.hours)))

        container.awaited({ cells() }) { it == "3" } shouldBe "3"
        container.remove()
    }

    @Test
    fun a_scan_labels_each_group_before_its_hosts_in_the_order_of_the_groups() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now, Kind.SOCKET), host(2, now), host(3, now, Kind.ROUTER), host(4, now, Kind.LAPTOP)), job = job)

        val container = rendered { scan(source, events) }

        container.awaited({ cellTexts() }) { it.size == 8 && "" !in it } shouldContainExactly listOf(
            "Network", "192.0.2.3", "Computers", "192.0.2.4", "Smart home", "192.0.2.1", "Other", "192.0.2.2",
        )
        container.remove()
    }

    @Test
    fun a_scan_orders_the_hosts_of_a_group_by_the_numbers_of_their_ips() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(200, now), host(10, now), host(9, now)), job = job)

        val container = rendered { scan(source, events) }

        container.awaited({ cellTexts() }) { it.size == 4 && "" !in it } shouldContainExactly listOf("Other", "192.0.2.9", "192.0.2.10", "192.0.2.200")
        container.remove()
    }

    @Test
    fun a_scan_counts_the_group_labels_among_the_cells() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now, Kind.SOCKET), host(2, now, Kind.LAMP), host(3, now)), job = job)

        val container = rendered { scan(source, events) }

        container.awaited({ cells() }) { it == "5" } shouldBe "5"
        container.remove()
    }

    @Test
    fun a_host_that_changes_its_kind_moves_to_its_new_group() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now, Kind.ROUTER), host(2, now)), job = job)
        val container = rendered { scan(source, events) }
        container.awaited({ cellTexts() }) { it.size == 4 }

        events.update(scan(now, host(1, now, Kind.ROUTER), host(2, now, Kind.NETWORK_SWITCH)))

        container.awaited({ cellTexts() }) { it.size == 3 && "" !in it } shouldContainExactly listOf("Network", "192.0.2.1", "192.0.2.2")
        container.remove()
    }

    @Test
    fun a_host_writes_the_length_of_each_line_it_fits() = runTest {
        val now = Clock.System.now()
        val named = Host(ip = IP.of("192.0.2.1"), name = "printer.local.", vendor = "Acme", status = Status.UP, since = now)
        val store = RootStore(listOf(named), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.awaited({ fitLengths() }) { it.size == 3 } shouldContainExactly listOf("7", "4", "9")
        container.remove()
    }

    @Test
    fun a_host_without_name_and_vendor_fits_the_placeholders() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.awaited({ fitLengths() }) { it.size == 2 } shouldContainExactly listOf("16", "9")
        container.remove()
    }

    @Test
    fun a_host_without_name_and_model_shows_the_end_of_its_mac() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), mac = "dc:00:00:00:00:12", status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("00:00:12") shouldContain "00:00:12"
        container.remove()
    }

    @Test
    fun a_host_without_name_model_and_mac_shows_the_placeholder() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("❔") shouldContain "❔"
        container.remove()
    }

    @Test
    fun a_named_host_does_not_show_its_mac() = runTest {
        val now = Clock.System.now()
        val named = Host(ip = IP.of("192.0.2.1"), name = "printer.local.", mac = "dc:00:00:00:00:12", status = Status.UP, since = now)
        val store = RootStore(listOf(named), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("printer") shouldNotContain "00:00:12"
        container.remove()
    }

    @Test
    fun a_host_fits_its_model_by_the_longest_word() = runTest {
        val codes = DeviceModelCodes(models = mapOf("Mac14,8" to DeviceModelCodes.Model(description = "Mac Studio", symbol = null)))
        DeviceModelCodes.set(codes)
        try {
            val now = Clock.System.now()
            val studio = Host(ip = IP.of("192.0.2.1"), model = "Mac14,8", status = Status.UP, since = now)
            val store = RootStore(listOf(studio), job = job)

            val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

            container.awaited({ fitLengths() }) { it.size == 4 } shouldContainExactly listOf("6", "10", "16", "9")
            container.remove()
        } finally {
            DeviceModelCodes.set(DeviceModelCodes())
        }
    }

    @Test
    fun a_host_without_a_model_code_is_drawn_by_its_kind() = runTest {
        withIcons {
            val now = Clock.System.now()
            val plug = Host(ip = IP.of("192.0.2.1"), kind = Kind.SOCKET, status = Status.UP, since = now)
            val store = RootStore(listOf(plug), job = job)

            val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

            container.awaited({ symbolNames() }) { it.isNotEmpty() } shouldContainExactly listOf("mdi:power-socket-eu")
            container.remove()
        }
    }

    @Test
    fun a_specific_brand_icon_beats_the_kind_and_an_apple_code_beats_both() = runTest {
        withIcons {
            val now = Clock.System.now()
            val stick = Host(ip = IP.of("192.0.2.1"), vendor = "Amazon", model = "Fire TV Stick 4K", kind = Kind.SET_TOP_BOX, status = Status.UP, since = now)
            val ipad = Host(ip = IP.of("192.0.2.2"), vendor = "Apple", model = "iPad8,3", kind = Kind.TABLET, status = Status.UP, since = now)
            val store = RootStore(listOf(stick, ipad), job = job)

            val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

            container.awaited({ symbolNames() }) { it.size == 2 } shouldContainExactly listOf("ipad", "cbi:firetv")
            container.remove()
        }
    }

    @Test
    fun a_custom_model_code_that_is_no_apple_code_does_not_hide_the_brand_icon() = runTest {
        withIcons {
            val now = Clock.System.now()
            val speaker = Host(ip = IP.of("192.0.2.1"), vendor = "Sonos", model = "One SL", kind = Kind.SPEAKER, status = Status.UP, since = now)
            val store = RootStore(listOf(speaker), job = job)

            val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

            container.awaited({ symbolNames() }) { it.isNotEmpty() } shouldContainExactly listOf("cbi:sonos-one")
            container.remove()
        }
    }

    @Test
    fun a_nameless_modelless_host_is_captioned_by_its_kind() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), kind = Kind.GAMING_DEVICE, mac = "80:00:00:00:00:0d", status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("Gaming Device") shouldContain "Gaming Device"
        container.remove()
    }

    @Test
    fun a_host_with_a_link_shows_the_badge_with_its_speed() = runTest {
        withIcons {
            val now = Clock.System.now()
            val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), link = Link.ETHERNET, speed = LinkSpeed(2500), status = Status.UP, since = now)), job = job)

            val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

            container.textOnce("2.5 Gbit/s") shouldContain "2.5 Gbit/s"
            container.querySelector(".host__link svg")?.getAttribute("data-symbol-name") shouldBe "mdi:ethernet"
            container.remove()
        }
    }

    @Test
    fun a_host_without_a_link_shows_no_badge() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.0.2.1"), status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("192.0.2.1")
        container.querySelector(".host__link") shouldBe null
        container.remove()
    }

    @Test
    fun a_card_carries_no_zoom() = runTest {
        val now = Clock.System.now()
        val events = RootStore(scan(now, host(1, now - 30.seconds), host(2, now - 3.hours)), job = job)

        val container = rendered { scan(source, events) }
        container.awaited({ hostIps(".hosts") }) { it.size == 2 } shouldContainExactly listOf("192.0.2.1", "192.0.2.2")
        nextFrames(5)

        container.querySelectorAll("[style*=zoom], [data-zoomed], [data-zooming]").length shouldBe 0
        container.remove()
    }
}

private val source = EventSource("test", "en0", Cidr.parse("192.0.2.0/24"))

private fun host(index: Int, since: Instant, kind: Kind? = null) = Host(ip = IP.of("192.0.2.$index"), kind = kind, status = Status.UP, since = since)

private fun scan(now: Instant, vararg hosts: Host) = ScanEvent(ScanEvent.Type.COMPLETED, hosts.toList(), now)

private fun HTMLElement.cells(): String =
    (querySelector(".hosts") as? HTMLElement)?.style?.getPropertyValue("--cells")?.trim().orEmpty()

private fun HTMLElement.cellTexts(): List<String> =
    querySelectorAll(".hosts > li").asList().map { cell -> ((cell as Element).querySelector(".hosts__label, .host__ip") ?: cell).textContent.orEmpty() }

private fun HTMLElement.hostIps(section: String): List<String> =
    querySelectorAll("$section .font-mono").asList().map { it.textContent.orEmpty() }

private fun HTMLElement.ages(): List<String> =
    querySelectorAll(".host[data-age]").asList().mapNotNull { (it as Element).getAttribute("data-age") }

private fun HTMLElement.fitLengths(): List<String> =
    querySelectorAll(".fit").asList().map { (it as HTMLElement).style.getPropertyValue("--len").trim() }

private fun Node.nodes(): List<Node> = childNodes.asList().flatMap { listOf(it) + it.nodes() }

private fun HTMLElement.symbolNames(): List<String> =
    querySelectorAll(".host__icon").asList().mapNotNull { (it as? Element)?.getAttribute("data-symbol-name") }

private suspend fun withIcons(block: suspend () -> Unit) {
    DeviceModelCodes.set(DeviceModelCodes(models = mapOf("iPad8,3" to DeviceModelCodes.Model("iPad Pro", "ipad"), "One SL" to DeviceModelCodes.Model("One SL", "hifispeaker")), symbols = mapOf("ipad" to """<svg data-symbol-name="ipad" viewBox="0 0 10 10"><path d="M0 0"/></svg>""", "hifispeaker" to """<svg data-symbol-name="hifispeaker" viewBox="0 0 10 10"><path d="M0 0"/></svg>""")))
    DeviceIcons.set(
        DeviceIcons(
            kinds = mapOf("Socket" to "mdi:power-socket-eu", "SetTopBox" to "mdi:cast", "Tablet" to "mdi:tablet"),
            specific = listOf(
                DeviceIcons.Matcher(vendor = "^Amazon$", model = "Fire TV", symbol = "cbi:firetv"),
                DeviceIcons.Matcher(vendor = "^Sonos$", model = "^One", symbol = "cbi:sonos-one"),
            ),
            symbols = listOf("mdi:power-socket-eu", "mdi:cast", "mdi:tablet", "cbi:firetv", "cbi:sonos-one", "mdi:ethernet", "mdi:wifi")
                .associateWith { """<svg data-symbol-name="$it" viewBox="0 0 24 24"><path d="M0 0"/></svg>""" },
        ),
    )
    try {
        block()
    } finally {
        DeviceModelCodes.set(DeviceModelCodes())
        DeviceIcons.set(DeviceIcons())
    }
}
