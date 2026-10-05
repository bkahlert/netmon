package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.Link
import com.bkahlert.netmon.LinkSpeed
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.epoch
import com.bkahlert.netmon.invoke
import com.bkahlert.netmon.nmap.NmapXml
import io.kotest.assertions.asClue
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ScanResultTest {

    @Test
    fun merge_scan_timestamp() = mergingShould(
        old = emptyList(),
        new = emptyList(),
    ) { merged, _ ->
        merged.timestamp shouldBe 200.epoch
    }

    @Test
    fun kind_link_and_speed_follow_the_scan_and_fall_back_to_the_record() = mergingShould(
        old = listOf(Host(ip = "10.0.0.1", mac = "aa:bb:cc:dd:ee:01", kind = Kind.LAMP, link = Link.WIFI, speed = LinkSpeed(65))),
        new = listOf(Host(ip = "10.0.0.1", mac = "aa:bb:cc:dd:ee:01", kind = Kind.SOCKET, link = null, speed = null)),
    ) { merged, _ ->
        merged.hosts.single() should {
            it.kind shouldBe Kind.SOCKET
            it.link shouldBe Link.WIFI
            it.speed shouldBe LinkSpeed(65)
        }
    }

    @Test
    fun ip() = mergingShould(
        old = listOf(
            Host(
                ip = "10.0.0.1",
                name = "unchanged-host",
                status = Status.UP,
                since = null,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.2",
                name = "old-name",
                status = Status.UP,
                since = null,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.3",
                name = "foo",
                status = Status.UP,
                since = null,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            )
        ),
        new = listOf(
            Host(
                ip = "10.0.0.1",
                name = "unchanged-host",
                status = Status.UP,
                since = null,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.2",
                name = "new-name",
                status = Status.UP,
                since = null,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.4",
                name = "bar",
                status = Status.UP,
                since = null,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            )
        ),
    ) { merged, changed ->
        merged.hosts.shouldContainExactly(
            Host(
                ip = "10.0.0.1",
                name = "unchanged-host",
                status = Status.UP,
                since = 100.epoch,
                lastSeen = 200.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.2",
                name = "new-name",
                status = Status.UP,
                since = 100.epoch,
                lastSeen = 200.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.3",
                name = "foo",
                status = Status.UP,
                since = 100.epoch,
                lastSeen = 100.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.4",
                name = "bar",
                status = Status.UP,
                since = 200.epoch,
                lastSeen = 200.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
        )
        changed.shouldContainExactly(
            Host(
                ip = "10.0.0.4",
                name = "bar",
                status = Status.UP,
                since = 200.epoch,
                lastSeen = 200.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
        )
    }

    @Test
    fun merge_name() = runTest {
        forAll(
            row(Status.UP, Status.UP, "bar"),
            row(Status.UP, Status.DOWN, "foo"),
            row(Status.UP, null, "bar"),
            row(Status.DOWN, Status.UP, "bar"),
            row(Status.DOWN, Status.DOWN, "foo"),
            row(Status.DOWN, null, "bar"),
            row(null, Status.UP, "bar"),
            row(null, Status.DOWN, "foo"),
            row(null, null, "bar"),
        ) { oldStatus, newStatus, expected ->
            mergingSingleShould(
                old = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = oldStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
                new = Host(
                    ip = "10.0.0.1",
                    name = "bar",
                    status = newStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
            ) { merged, _ ->
                merged.name shouldBe expected
            }
        }
    }

    @Test
    fun merge_status() = runTest {
        forAll(
            row(Status.UP, Status.UP, Status.UP),
            row(Status.UP, Status.DOWN, Status.UP),
            row(Status.UP, null, Status.UP),
            row(Status.DOWN, Status.UP, Status.UP),
            row(Status.DOWN, Status.DOWN, Status.DOWN),
            row(Status.DOWN, null, Status.UP),
            row(null, Status.UP, Status.UP),
            row(null, Status.DOWN, Status.UP),
            row(null, null, Status.UP),
        ) { oldStatus, newStatus, expected ->
            mergingSingleShould(
                old = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = oldStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
                new = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = newStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
            ) { merged, _ ->
                merged.status shouldBe expected
            }
        }
    }

    @Test
    fun merge_since() = runTest {
        forAll(
            row(Status.UP, Status.UP, 100.epoch),
            row(Status.UP, Status.DOWN, 100.epoch),
            row(Status.UP, null, 100.epoch),
            row(Status.DOWN, Status.UP, 200.epoch),
            row(Status.DOWN, Status.DOWN, 100.epoch),
            row(Status.DOWN, null, 200.epoch),
            row(null, Status.UP, 100.epoch),
            row(null, Status.DOWN, 100.epoch),
            row(null, null, 100.epoch),
        ) { oldStatus, newStatus, expected ->
            mergingSingleShould(
                old = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = oldStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
                new = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = newStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
            ) { merged, _ ->
                merged.since shouldBe expected
            }
        }
    }

    @Test
    fun merge_model() = runTest {
        forAll(
            row(Status.UP, Status.UP, "Bar10,6"),
            row(Status.UP, Status.DOWN, "FooPro6,1"),
            row(Status.UP, null, "Bar10,6"),
            row(Status.DOWN, Status.UP, "Bar10,6"),
            row(Status.DOWN, Status.DOWN, "FooPro6,1"),
            row(Status.DOWN, null, "Bar10,6"),
            row(null, Status.UP, "Bar10,6"),
            row(null, Status.DOWN, "FooPro6,1"),
            row(null, null, "Bar10,6"),
        ) { oldStatus, newStatus, expected ->
            mergingSingleShould(
                old = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = oldStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
                new = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = newStatus,
                    since = null,
                    model = "Bar10,6",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
            ) { merged, _ ->
                merged.model shouldBe expected
            }
        }
    }

    @Test
    fun merge_vendor() = runTest {
        forAll(
            row(Status.UP, Status.UP, "Globex"),
            row(Status.UP, Status.DOWN, "ACME"),
            row(Status.UP, null, "Globex"),
            row(Status.DOWN, Status.UP, "Globex"),
            row(Status.DOWN, Status.DOWN, "ACME"),
            row(Status.DOWN, null, "Globex"),
            row(null, Status.UP, "Globex"),
            row(null, Status.DOWN, "ACME"),
            row(null, null, "Globex"),
        ) { oldStatus, newStatus, expected ->
            mergingSingleShould(
                old = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = oldStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb", "airplay"),
                ),
                new = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = newStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "Globex",
                    services = setOf("smb", "airplay"),
                ),
            ) { merged, _ ->
                merged.vendor shouldBe expected
            }
        }
    }

    @Test
    fun merge_services() = runTest {
        forAll(
            row(Status.UP, Status.UP, setOf("airplay", "smb")),
            row(Status.UP, Status.DOWN, setOf("smb")),
            row(Status.UP, null, setOf("airplay", "smb")),
            row(Status.DOWN, Status.UP, setOf("airplay", "smb")),
            row(Status.DOWN, Status.DOWN, setOf("smb")),
            row(Status.DOWN, null, setOf("airplay", "smb")),
            row(null, Status.UP, setOf("airplay", "smb")),
            row(null, Status.DOWN, setOf("smb")),
            row(null, null, setOf("airplay", "smb")),
        ) { oldStatus, newStatus, expected ->
            mergingSingleShould(
                old = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = oldStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("smb"),
                ),
                new = Host(
                    ip = "10.0.0.1",
                    name = "foo",
                    status = newStatus,
                    since = null,
                    model = "FooPro6,1",
                    vendor = "ACME",
                    services = setOf("airplay", "smb"),
                ),
            ) { merged, _ ->
                merged.services shouldBe expected
            }
        }
    }
    @Test
    fun save_replaces_a_relative_file_and_leaves_no_temp_file() {
        val directory = Paths.get("build", "tmp", "ScanResultTest").createDirectories()
        try {
            val file = directory.resolve("scan.json").also { it.writeText("stale") }
            val scan = scanAt(100.epoch, Host(status = Status.UP, since = 100.epoch, lastSeen = 100.epoch))

            scan.save(file)

            file should {
                ScanResult.load(it) shouldBe scan
                directory.listDirectoryEntries().shouldContainExactly(listOf(it))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun merge_unseen_just_before_downAfter_keeps_host_up() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(279.epoch))

        merged should { m ->
            m.host shouldBe recorded
            m.changed.shouldBeEmpty()
        }
    }

    @Test
    fun merge_unseen_for_downAfter_turns_host_down_since_lastSeen() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch)
        val expected = recorded.copy(status = Status.DOWN, since = 100.epoch)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(280.epoch))

        merged should { m ->
            m.host shouldBe expected
            m.changed.shouldContainExactly(expected)
        }
    }

    @Test
    fun merge_seen_again_after_down_turns_host_up_since_scan_time() {
        val recorded = Host(status = Status.DOWN, since = 100.epoch, lastSeen = 100.epoch)
        val expected = Host(status = Status.UP, since = 400.epoch, lastSeen = 400.epoch)

        val merged = scanAt(280.epoch, recorded).mergedWith(scanAt(400.epoch, Host(status = Status.UP, since = null)))

        merged should { m ->
            m.host shouldBe expected
            m.changed.shouldContainExactly(expected)
        }
    }

    @Test
    fun merge_down_host_stays_down_without_event() {
        val recorded = Host(status = Status.DOWN, since = 100.epoch, lastSeen = 100.epoch)

        val merged = scanAt(280.epoch, recorded).mergedWith(scanAt(1000.epoch))

        merged should { m ->
            m.host shouldBe recorded
            m.changed.shouldBeEmpty()
        }
    }

    @Test
    fun merge_seen_host_keeps_recorded_fields_the_scan_lacks() {
        val recorded = Host(
            name = "Anirul",
            status = Status.UP,
            since = 50.epoch,
            lastSeen = 100.epoch,
            model = "MacPro7,1",
            vendor = "Apple",
            services = setOf("smb"),
        )
        val scanned = Host(name = null, status = Status.UP, model = null, vendor = null, services = null)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(130.epoch, scanned))

        merged should { m ->
            m.host shouldBe recorded.copy(lastSeen = 130.epoch)
            m.changed.shouldBeEmpty()
        }
    }

    @Test
    fun merge_enrichment_change_fires_no_event() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch, model = "FooPro6,1")
        val scanned = Host(status = Status.UP, model = "Bar10,6")

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(130.epoch, scanned))

        merged should { m ->
            m.host.model shouldBe "Bar10,6"
            m.changed.shouldBeEmpty()
        }
    }

    @Test
    fun merge_legacy_host_without_lastSeen_stores_previous_scan_time() {
        val legacy = Host(status = Status.UP, since = 50.epoch, lastSeen = null)

        val merged = scanAt(100.epoch, legacy).mergedWith(scanAt(250.epoch))

        merged.host.lastSeen shouldBe 100.epoch
    }

    @Test
    fun merge_legacy_host_turns_down_after_downAfter_from_previous_scan_time() {
        val legacy = Host(status = Status.UP, since = 50.epoch, lastSeen = null)
        val stillUp = scanAt(100.epoch, legacy).mergedWith(scanAt(250.epoch)).result

        val merged = stillUp.mergedWith(scanAt(280.epoch))

        merged should { m ->
            m.host.status shouldBe Status.DOWN
            m.host.since shouldBe 100.epoch
        }
    }

    @Test
    fun merge_notBefore_after_lastSeen_extends_the_grace_period() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(280.epoch), notBefore = 200.epoch)

        merged.host.status shouldBe Status.UP
    }

    @Test
    fun merge_notBefore_before_lastSeen_changes_nothing() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(280.epoch), notBefore = 50.epoch)

        merged.host.status shouldBe Status.DOWN
    }

    @Test
    fun merge_empty_scan_within_downAfter_keeps_every_host_up() {
        val recorded = listOf("10.0.0.1", "10.0.0.2", "10.0.0.3").map {
            Host(ip = it, status = Status.UP, since = 50.epoch, lastSeen = 100.epoch)
        }
        val changed = mutableListOf<Host>()

        val merged = scanAt(100.epoch, *recorded.toTypedArray()).merge(scanAt(150.epoch), 3.minutes, Instant.DISTANT_PAST) { changed.add(it) }

        merged.hosts should {
            it.shouldContainExactly(recorded)
            changed.shouldBeEmpty()
        }
    }

    @Test
    fun merge_scan_time_before_lastSeen_keeps_host_up() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 1000.epoch)

        val merged = scanAt(1000.epoch, recorded).mergedWith(scanAt(10.epoch))

        merged.host.status shouldBe Status.UP
    }

    @Test
    fun merge_up_host_without_since_gets_scan_time_when_seen() {
        val recorded = Host(status = Status.UP, since = null, lastSeen = null)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(200.epoch, Host(status = Status.UP)))

        merged.host.since shouldBe 200.epoch
    }

    @Test
    fun merge_recorded_without_status_turns_down_when_unseen() {
        val recorded = Host(status = null, since = null)
        val expected = recorded.copy(status = Status.DOWN, since = 200.epoch)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(200.epoch))

        merged should { m ->
            m.host shouldBe expected
            m.changed.shouldContainExactly(expected)
        }
    }

    @Test
    fun merge_recorded_unknown_status_turns_up_when_seen() {
        val recorded = Host(status = Status.UNKNOWN("odd"), since = 50.epoch)
        val expected = Host(status = Status.UP, since = 200.epoch, lastSeen = 200.epoch)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(200.epoch, Host(status = Status.UP)))

        merged should { m ->
            m.host shouldBe expected
            m.changed.shouldContainExactly(expected)
        }
    }

    @Test
    fun merge_scanned_non_up_host_without_record_is_down_without_lastSeen() = runTest {
        forAll(
            row(Status.DOWN),
            row(Status.UNKNOWN("odd")),
        ) { status ->
            val merged = scanAt(100.epoch).mergedWith(scanAt(200.epoch, Host(status = status)))

            merged should { m ->
                m.host.status shouldBe Status.DOWN
                m.host.since shouldBe 200.epoch
                m.host.lastSeen shouldBe null
                m.changed.size shouldBe 1
            }
        }
    }

    @Test
    fun merge_host_that_moved_keeps_its_record_under_the_new_ip() {
        val recorded = Host(
            ip = "10.0.0.1", name = "Anirul", status = Status.UP, since = 50.epoch, lastSeen = 100.epoch,
            model = "MacPro7,1", vendor = "Apple", services = setOf("smb"), mac = "aa:bb:cc:dd:ee:01",
        )
        val scanned = Host(
            ip = "10.0.0.5", name = null, status = Status.UP, model = null, vendor = null, services = null, mac = "aa:bb:cc:dd:ee:01",
        )

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(130.epoch, scanned))

        merged should { m ->
            m.host shouldBe recorded.copy(ip = IP.of("10.0.0.5"), lastSeen = 130.epoch)
            m.changed.shouldBeEmpty()
        }
    }

    @Test
    fun merge_new_device_on_a_taken_ip_inherits_nothing_and_replaces_the_old_host() {
        val recorded = Host(
            ip = "10.0.0.1", name = "Anirul", status = Status.UP, since = 50.epoch, lastSeen = 100.epoch,
            model = "MacPro7,1", vendor = "Apple", services = setOf("smb"), mac = "aa:bb:cc:dd:ee:01",
        )
        val scanned = Host(
            ip = "10.0.0.1", name = null, status = Status.UP, model = null, vendor = null, services = null, mac = "aa:bb:cc:dd:ee:02",
        )
        val expected = Host(
            ip = "10.0.0.1", name = null, status = Status.UP, since = 130.epoch, lastSeen = 130.epoch,
            model = null, vendor = null, services = null, mac = "aa:bb:cc:dd:ee:02",
        )

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(130.epoch, scanned))

        merged should { m ->
            m.result.hosts.shouldContainExactly(expected)
            m.changed.shouldContainExactly(expected)
        }
    }

    @Test
    fun merge_host_unseen_at_an_ip_nobody_took_keeps_the_grace_period() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch, mac = "aa:bb:cc:dd:ee:01")
        val other = Host(ip = "10.0.0.2", status = Status.UP, mac = "aa:bb:cc:dd:ee:02")

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(130.epoch, other))

        merged.result.hosts.map { it.ip.toString() to it.status } shouldContainExactly listOf("10.0.0.1" to Status.UP, "10.0.0.2" to Status.UP)
    }

    @Test
    fun merge_hosts_that_swapped_ips_keep_their_own_records() {
        val first = Host(ip = "10.0.0.1", name = "first", status = Status.UP, since = 50.epoch, lastSeen = 100.epoch, mac = "aa:bb:cc:dd:ee:01")
        val second = Host(ip = "10.0.0.2", name = "second", status = Status.UP, since = 60.epoch, lastSeen = 100.epoch, mac = "aa:bb:cc:dd:ee:02")
        val scannedFirst = Host(ip = "10.0.0.2", name = null, status = Status.UP, mac = "aa:bb:cc:dd:ee:01", model = null, vendor = null, services = null)
        val scannedSecond = Host(ip = "10.0.0.1", name = null, status = Status.UP, mac = "aa:bb:cc:dd:ee:02", model = null, vendor = null, services = null)

        val merged = scanAt(100.epoch, first, second).mergedWith(scanAt(130.epoch, scannedFirst, scannedSecond))

        merged.result.hosts.map { Triple(it.ip.toString(), it.name, it.since) } shouldContainExactly listOf(
            Triple("10.0.0.1", "second", 60.epoch),
            Triple("10.0.0.2", "first", 50.epoch),
        )
    }

    @Test
    fun merge_sleep_proxy_answering_for_a_sleeping_host_keeps_both_records() {
        val proxy = Host(ip = "10.0.0.8", name = "HomePod", status = Status.UP, since = 50.epoch, lastSeen = 100.epoch, mac = "aa:bb:cc:dd:ee:08")
        val sleeper = Host(ip = "10.0.0.50", name = "MacBook", status = Status.UP, since = 60.epoch, lastSeen = 100.epoch, mac = "aa:bb:cc:dd:ee:50")
        val scanned = NmapXml.parse(
            """<nmaprun>
            |<host><status state="up"/><address addr="10.0.0.8" addrtype="ipv4"/><address addr="AA:BB:CC:DD:EE:08" addrtype="mac"/></host>
            |<host><status state="up"/><address addr="10.0.0.50" addrtype="ipv4"/><address addr="AA:BB:CC:DD:EE:08" addrtype="mac"/></host>
            |</nmaprun>""".trimMargin(),
        )

        val merged = scanAt(100.epoch, proxy, sleeper).mergedWith(scanAt(130.epoch, *scanned.toTypedArray()))

        merged.result.hosts.map { Triple(it.ip.toString(), it.name to it.mac, it.since) } shouldContainExactly listOf(
            Triple("10.0.0.8", "HomePod" to "aa:bb:cc:dd:ee:08", 50.epoch),
            Triple("10.0.0.50", "MacBook" to "aa:bb:cc:dd:ee:50", 60.epoch),
        )
        merged.changed.shouldBeEmpty()
    }

    @Test
    fun merge_scan_without_mac_matches_a_recorded_host_by_ip_and_keeps_its_mac() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch, mac = "aa:bb:cc:dd:ee:01")
        val scanned = Host(status = Status.UP, mac = null)

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(130.epoch, scanned))

        merged should { m ->
            m.host shouldBe recorded.copy(lastSeen = 130.epoch)
            m.changed.shouldBeEmpty()
        }
    }

    @Test
    fun merge_scan_with_mac_matches_a_recorded_host_without_mac_by_ip_and_adopts_the_mac() {
        val recorded = Host(status = Status.UP, since = 50.epoch, lastSeen = 100.epoch, mac = null)
        val scanned = Host(status = Status.UP, mac = "aa:bb:cc:dd:ee:01")

        val merged = scanAt(100.epoch, recorded).mergedWith(scanAt(130.epoch, scanned))

        merged should { m ->
            m.host shouldBe recorded.copy(lastSeen = 130.epoch, mac = "aa:bb:cc:dd:ee:01")
            m.changed.shouldBeEmpty()
        }
    }
}


/**
 * Asserts the result [ScanResult.merge] operation based on the specified [old] and [new]
 * using the specified [assertion].
 */
private fun mergingShould(
    old: List<Host>,
    new: List<Host>,
    downAfter: Duration = 3.minutes,
    notBefore: Instant = Instant.DISTANT_PAST,
    assertion: (ScanResult, List<Host>) -> Unit,
) {
    val initialScan = ScanResult(
        `interface` = "en0",
        cidr = Cidr.parse("10.0.0.0/24"),
        timestamp = 0.epoch,
        hosts = emptyList(),
    )

    val oldScan = ScanResult(
        `interface` = "en0",
        cidr = Cidr.parse("10.0.0.0/24"),
        timestamp = 100.epoch,
        hosts = old,
    )

    val newScan = ScanResult(
        `interface` = "en0",
        cidr = Cidr.parse("10.0.0.0/24"),
        timestamp = 200.epoch,
        hosts = new,
    )

    val changed = mutableListOf<Host>()
    val merged = initialScan
        .merge(
            currentResult = oldScan,
            downAfter = downAfter,
            notBefore = notBefore,
            onChange = {},
        )
        .merge(
            currentResult = newScan,
            downAfter = downAfter,
            notBefore = notBefore,
            onChange = { changed.add(it) },
        )

    assertion(merged, changed)
}

/**
 * Asserts the result [ScanResult.merge] operation based on the specified [old] and [new]
 * using the specified [assertion].
 */
private fun mergingSingleShould(
    old: Host?,
    new: Host?,
    assertion: (Host, Host?) -> Unit,
) {
    check(old != null || new != null) { "At least one of old and new must be non-null" }
    mergingShould(listOfNotNull(old), listOfNotNull(new)) { merged, changes ->
        changes.asClue { it.size }.shouldBeBetween(0, 1)
        merged.hosts.shouldBeSingleton().first() should {
            assertion(it, changes.singleOrNull())
        }
    }
}

private fun scanAt(timestamp: Instant, vararg hosts: Host) = ScanResult(
    `interface` = "en0",
    cidr = Cidr.parse("10.0.0.0/24"),
    timestamp = timestamp,
    hosts = hosts.toList(),
)

private class Merged(val result: ScanResult, val changed: List<Host>) {
    val host: Host get() = result.hosts.single()
}

private fun ScanResult.mergedWith(
    current: ScanResult,
    downAfter: Duration = 3.minutes,
    notBefore: Instant = Instant.DISTANT_PAST,
): Merged {
    val changed = mutableListOf<Host>()
    val result = merge(current, downAfter, notBefore) { changed.add(it) }
    return Merged(result, changed)
}
