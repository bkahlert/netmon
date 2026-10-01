package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.epoch
import com.bkahlert.netmon.invoke
import io.kotest.assertions.asClue
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ScanResultTest {

    @Test
    fun merge_scan_timestamp() = mergingShould(
        old = emptyList(),
        new = emptyList(),
    ) { merged, _ ->
        merged.timestamp shouldBe 200.epoch
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
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.2",
                name = "new-name",
                status = Status.UP,
                since = 100.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.3",
                name = "foo",
                status = Status.DOWN,
                since = 200.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.4",
                name = "bar",
                status = Status.UP,
                since = 200.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
        )
        changed.shouldContainExactly(
            Host(
                ip = "10.0.0.2",
                name = "new-name",
                status = Status.UP,
                since = 100.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.3",
                name = "foo",
                status = Status.DOWN,
                since = 200.epoch,
                model = "FooPro6,1",
                vendor = "ACME",
                services = setOf("smb", "airplay"),
            ),
            Host(
                ip = "10.0.0.4",
                name = "bar",
                status = Status.UP,
                since = 200.epoch,
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
            row(Status.DOWN, Status.DOWN, null),
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
            row(Status.UP, Status.DOWN, Status.DOWN),
            row(Status.UP, null, Status.UP),
            row(Status.DOWN, Status.UP, Status.UP),
            row(Status.DOWN, Status.DOWN, Status.DOWN),
            row(Status.DOWN, null, Status.UP),
            row(null, Status.UP, Status.UP),
            row(null, Status.DOWN, Status.DOWN),
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
            row(Status.UP, Status.UP, 100.epoch), // "still up"
            row(Status.UP, Status.DOWN, 200.epoch), // "down again"
            row(Status.UP, null, 100.epoch), // now unknown, but since the host is in the new scan, it's still considered "up"
            row(Status.DOWN, Status.UP, 200.epoch), // "up again"
            row(Status.DOWN, Status.DOWN, 100.epoch), // "still down"
            row(Status.DOWN, null, 200.epoch), // unknown, but since it's in the new scan, it's considered "up"
            row(null, Status.UP, 100.epoch), // "finally, officially up"
            row(null, Status.DOWN, 200.epoch), // "finally, officially down"
            row(null, null, 100.epoch), // "unknown since"
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
            row(Status.DOWN, Status.DOWN, null),
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
            row(Status.DOWN, Status.DOWN, null),
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
            row(Status.DOWN, Status.DOWN, null),
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
}


/**
 * Asserts the result [ScanResult.merge] operation based on the specified [old] and [new]
 * using the specified [assertion].
 */
private fun mergingShould(
    old: List<Host>,
    new: List<Host>,
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
            onChange = {},
        )
        .merge(
            currentResult = newScan,
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
