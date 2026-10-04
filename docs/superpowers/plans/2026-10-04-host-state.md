# Stable Host State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A host keeps its state across missed scans and scanner restarts: it turns DOWN only after 3 minutes unseen, keeps the
fields a fresh scan lacks, and raises a host event only for a new host or an UP/DOWN change.

**Architecture:** `Host` gains `lastSeen`. `ScanResult.merge` becomes a per-host rule table driven by `downAfter` and a
restart floor `notBefore`; `NetmonScanner` feeds it `ScannerSettings.downAfter` and its own construction time. The state
file is written atomically next to its target, and `Application` drops a network's scanner when the network goes away.

**Tech Stack:** Kotlin Multiplatform (`commonMain` for `Host`, `jvmMain` for the scanner), kotlinx.serialization,
kotlin.test with Kotest matchers, Gradle.

**Spec:** [2026-10-04-host-state-design.md](../specs/2026-10-04-host-state-design.md)

## Global Constraints

- Grace before DOWN: `ScannerSettings.downAfter`, default `3.minutes`; UP is immediate.
- `Host.lastSeen` is `Instant?`, default `null`, serialized as epoch seconds like `since`; state files without it load.
- A host event fires for a host without a recorded counterpart and for a changed status, nothing else.
- The restart floor is the `NetmonScanner`'s construction time; `since` of a late DOWN is the recorded `lastSeen`, not the floor.
- The state file's temp file is created in the target's own directory and moved with `ATOMIC_MOVE`.
- Run Gradle in the foreground, one build at a time: no IDE Gradle sync or background `./gradlew` alongside.
- Tests follow the surrounding file: `kotlin.test` `@Test` functions, Kotest matchers, helpers at the bottom of the file.
- Commits are Conventional Commits with scope `scanner`, work stays on the current branch `docs/host-state-spec`.

## Review Focus

Failure modes the spec implies; each has its test in the task named.

- **Clock behind `lastSeen`.** The Pi has no clock until NTP answers, so a scan time can precede `lastSeen`: the host must
  stay UP, not throw (Task 2, `merge_scan_time_before_lastSeen_keeps_host_up`).
- **Empty scan.** nmap fails or the link just dropped: every host stays UP within the grace, no event storm (Task 2,
  `merge_empty_scan_within_downAfter_keeps_every_host_up`).
- **Legacy state file.** Hosts without `lastSeen` must still reach DOWN, not have the stand-in advance forever (Task 2,
  `merge_legacy_host_turns_down_after_downAfter_from_previous_scan_time`).
- **First run, no state file.** The initial scan records hosts without `since`; they get the scan time on the next scan
  (Task 2, `merge_up_host_without_since_gets_scan_time_when_seen`).
- **IP reuse inside the grace period.** A new device on a departed device's address inherits the recorded fields it lacks
  itself. Accepted until slice B identifies hosts by MAC; no test.

---

### Task 1: `Host.lastSeen`

**Files:**
- Modify: `src/commonMain/kotlin/com/bkahlert/netmon/Host.kt`
- Test: `src/commonTest/kotlin/com/bkahlert/netmon/HostTest.kt`

**Interfaces:**
- Produces: `Host.lastSeen: Instant?` as the last constructor parameter (after `services`), and the test helper
  `Host.Companion.invoke(..., lastSeen: Instant? = null)` with nullable `name`, `model`, `vendor`, `services`.

- [ ] **Step 1: Extend the test helper and write the failing test**

In `HostTest.kt`, replace the helper `Host.Companion.invoke` with:

```kotlin
operator fun Host.Companion.invoke(
    ip: String = "10.0.0.1",
    name: String? = "foo",
    status: Status? = Status.UP,
    since: Instant? = null,
    model: String? = "FooPro6,1",
    vendor: String? = "ACME",
    services: Set<String>? = setOf("smb", "airplay"),
    lastSeen: Instant? = null,
) = Host(
    ip = IP.of(ip),
    name = name,
    status = status,
    since = since,
    model = model,
    vendor = vendor,
    services = services,
    lastSeen = lastSeen,
)
```

Add inside `class HostTest`, after `from_json_down`:

```kotlin
    @Test
    fun last_seen_round_trips_as_epoch_seconds() {
        val host = Host(ip = IP.of("10.0.0.1"), status = Status.UP, since = 1690159731L.epoch, lastSeen = 1690159800L.epoch)

        val json = JsonFormat.encodeToString(Host.serializer(), host)

        json shouldContain "\"lastSeen\": 1690159800"
        JsonFormat.decodeFromString(Host.serializer(), json) shouldBe host
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.HostTest'`
Expected: FAIL, compilation error: no parameter `lastSeen` in `Host`.

- [ ] **Step 3: Add the field**

In `Host.kt`, add as the last constructor parameter (after `services`):

```kotlin
    /** The time of the last scan that found the host up. */
    @SerialName("lastSeen") @Serializable(InstantAsEpochSecondsSerializer::class) val lastSeen: Instant? = null,
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.HostTest'`
Expected: PASS, including `from_json_up` and `from_json_down` (state without `lastSeen` still decodes).

- [ ] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/com/bkahlert/netmon/Host.kt src/commonTest/kotlin/com/bkahlert/netmon/HostTest.kt
git commit -m "feat(scanner): record when a host was last seen"
```

---

### Task 2: Merge rules, `downAfter` and the restart floor

**Files:**
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt` (`merge`)
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScannerSettings.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/NetmonScanner.kt`
- Modify: `devices/README.md`
- Create: `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/ScannerSettingsTest.kt`
- Test: `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/ScanResultTest.kt`

**Interfaces:**
- Consumes: `Host.lastSeen` and the test helper `Host(...)` from Task 1.
- Produces: `ScannerSettings.downAfter: Duration`;
  `ScanResult.merge(currentResult: ScanResult, downAfter: Duration, notBefore: Instant, onChange: (Host) -> Unit = {}): ScanResult`.

- [ ] **Step 1: Write the failing settings test**

Create `ScannerSettingsTest.kt`:

```kotlin
package com.bkahlert.netmon.scanner

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class ScannerSettingsTest {

    @Test
    fun down_after_defaults_to_three_minutes() {
        ScannerSettings.downAfter shouldBe 3.minutes
    }
}
```

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.scanner.ScannerSettingsTest'`
Expected: FAIL, unresolved reference `downAfter`.

- [ ] **Step 2: Add the setting**

Replace everything in `ScannerSettings.kt` after the `package` line with:

```kotlin
import com.bkahlert.kommons.config.Settings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Settings for the [NmapNetworkScanner]. */
object ScannerSettings : Settings("scanner") {

    val pauseDuration: Duration by setting(default = 30.seconds)

    /** The duration a host may stay unseen before it is reported DOWN. */
    val downAfter: Duration by setting(default = 3.minutes)
}
```

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.scanner.ScannerSettingsTest'`
Expected: PASS.

- [ ] **Step 3: Update `ScanResultTest` to the new semantics**

3a. Add imports: `io.kotest.matchers.collections.shouldBeEmpty`, `kotlin.time.Duration`,
`kotlin.time.Duration.Companion.minutes`, `kotlin.time.Instant`.

3b. Replace the harness `mergingShould` (the second `merge` now passes the new arguments; callers are unchanged):

```kotlin
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
```

3c. Append these helpers at the end of the file:

```kotlin
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
```

3d. Rewrite the `ip` test's final block (`{ merged, changed -> ... }`). Host `.3` is unseen for 100 s, under the 3
minutes, so it stays UP; only `.4` is an event:

```kotlin
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
```

3e. In `merge_name`, `merge_model`, `merge_vendor` and `merge_services`, change the expectation of the row
`row(Status.DOWN, Status.DOWN, null)`: a DOWN host with no record now keeps the scan's fields. The new value is the old
host's value: `"foo"` in `merge_name`, `"FooPro6,1"` in `merge_model`, `"ACME"` in `merge_vendor`, `setOf("smb")` in
`merge_services`. All other rows stay.

3f. In `merge_status`, replace the `forAll(` rows with:

```kotlin
            row(Status.UP, Status.UP, Status.UP),
            row(Status.UP, Status.DOWN, Status.UP),
            row(Status.UP, null, Status.UP),
            row(Status.DOWN, Status.UP, Status.UP),
            row(Status.DOWN, Status.DOWN, Status.DOWN),
            row(Status.DOWN, null, Status.UP),
            row(null, Status.UP, Status.UP),
            row(null, Status.DOWN, Status.UP),
            row(null, null, Status.UP),
```

3g. In `merge_since`, replace the rows with (and drop their trailing comments):

```kotlin
            row(Status.UP, Status.UP, 100.epoch),
            row(Status.UP, Status.DOWN, 100.epoch),
            row(Status.UP, null, 100.epoch),
            row(Status.DOWN, Status.UP, 200.epoch),
            row(Status.DOWN, Status.DOWN, 100.epoch),
            row(Status.DOWN, null, 200.epoch),
            row(null, Status.UP, 100.epoch),
            row(null, Status.DOWN, 100.epoch),
            row(null, null, 100.epoch),
```

3h. Add these tests inside `class ScanResultTest`, after `merge_services`:

```kotlin
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
```

- [ ] **Step 4: Add the parameters without using them, so the tests compile and fail on behavior**

In `ScanResult.kt` add imports `kotlin.time.Duration` and change the `merge` signature (body unchanged):

```kotlin
    fun merge(
        currentResult: ScanResult,
        downAfter: Duration,
        notBefore: Instant,
        onChange: (Host) -> Unit = {},
    ): ScanResult {
```

In `NetmonScanner.kt` add `import kotlin.time.Instant`, a property next to `scanResultFile`:

```kotlin
    private val startedAt: Instant = Clock.System.now()
```

and replace the merge call in `scan()`:

```kotlin
        oldScan.merge(currentScan, downAfter = ScannerSettings.downAfter, notBefore = startedAt, onChange = onChange)
            .also { onScan(it) }
            .also { it.save(scanResultFile) }
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.scanner.ScanResultTest'`
Expected: FAIL on the rewritten tables and the new tests (for example `merge_unseen_just_before_downAfter_keeps_host_up`
sees DOWN); the whole module compiles.

- [ ] **Step 6: Implement the merge rules**

In `ScanResult.kt`, replace `merge` and add `mergeHost` right after it:

```kotlin
    fun merge(
        currentResult: ScanResult,
        downAfter: Duration,
        notBefore: Instant,
        onChange: (Host) -> Unit = {},
    ): ScanResult {
        check(`interface` == currentResult.`interface`) { "Interfaces do not match: $`interface` != ${currentResult.`interface`}" }
        check(cidr == currentResult.cidr) { "Networks do not match: $cidr != ${currentResult.cidr}" }
        return ScanResult(
            `interface` = `interface`,
            cidr = cidr,
            hosts = buildSet {
                hosts.forEach { add(it.ip) }
                currentResult.hosts.forEach { add(it.ip) }
            }
                .sorted()
                .map { ip ->
                    val recordedHost = hosts.find { it.ip == ip }
                    val scannedHost = currentResult.hosts.find { it.ip == ip } // TODO improve detection, e.g. by MAC address and/or hostname
                    val mergedHost = mergeHost(recordedHost, scannedHost, currentResult.timestamp, downAfter, notBefore)
                    if (recordedHost == null || recordedHost.status != mergedHost.status) onChange(mergedHost)
                    mergedHost
                },
            timestamp = currentResult.timestamp,
        )
    }

    private fun mergeHost(recorded: Host?, scanned: Host?, scanTime: Instant, downAfter: Duration, notBefore: Instant): Host = when {
        scanned != null && (scanned.status == null || scanned.status == Status.UP) -> scanned.copy(
            name = scanned.name ?: recorded?.name,
            status = Status.UP,
            since = if (recorded != null && recorded.status == Status.UP) recorded.since ?: scanTime else scanTime,
            lastSeen = scanTime,
            model = scanned.model ?: recorded?.model,
            vendor = scanned.vendor ?: recorded?.vendor,
            services = scanned.services ?: recorded?.services,
        )

        recorded == null -> checkNotNull(scanned).copy(status = Status.DOWN, since = scanTime, lastSeen = null)

        recorded.status == Status.UP -> {
            val lastSeen = recorded.lastSeen ?: timestamp
            if (scanTime - maxOf(lastSeen, notBefore) < downAfter) recorded.copy(lastSeen = lastSeen)
            else recorded.copy(status = Status.DOWN, since = lastSeen, lastSeen = lastSeen)
        }

        recorded.status == Status.DOWN -> recorded

        else -> recorded.copy(status = Status.DOWN, since = scanTime)
    }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.scanner.*'`
Expected: PASS.

- [ ] **Step 8: Run all JVM unit tests**

Run: `make test-jvm`
Expected: PASS. A failure in a test outside `scanner/` that builds `Host(...)` or calls `merge` means a caller was missed; fix it
in this task.

- [ ] **Step 9: Document the setting**

In `devices/README.md`, the sentence that lists the scanner's overrides ends with the `NETWORK_MIN_HOST_BITS` and
`NETWORK_MAX_HOST_BITS` filter. Add `SCANNER_DOWN_AFTER` to that list, described as how long a host may stay unseen before
it shows as down (3 minutes by default). Keep the rest of the sentence as it is.

Run: `make test-tier0`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add src/jvmMain/kotlin/com/bkahlert/netmon/scanner src/jvmTest/kotlin/com/bkahlert/netmon/scanner devices/README.md
git commit -m "fix(scanner): keep a host's state across missed scans and restarts"
```

---

### Task 3: Atomic state file

**Files:**
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt` (`save`)
- Test: `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/ScanResultTest.kt`

**Interfaces:**
- Consumes: `scanAt` from Task 2's test helpers.
- Produces: `ScanResult.save(file, format)` writes through a temp file in `file.toAbsolutePath().parent` and moves it with
  `ATOMIC_MOVE`.

- [ ] **Step 1: Write the test**

Add imports `java.nio.file.Paths`, `kotlin.io.path.createDirectories`, `kotlin.io.path.deleteRecursively`,
`kotlin.io.path.listDirectoryEntries`, `kotlin.io.path.writeText`. Add inside `class ScanResultTest`:

```kotlin
    @Test
    fun save_replaces_a_relative_file_and_leaves_no_temp_file() {
        val directory = Paths.get("build", "tmp", "ScanResultTest").createDirectories()
        try {
            val file = directory.resolve("scan.json").also { it.writeText("stale") }
            val scan = scanAt(100.epoch, Host(status = Status.UP, since = 100.epoch, lastSeen = 100.epoch))

            scan.save(file)

            file should {
                ScanResult.load(it) shouldBe scan
                directory.listDirectoryEntries().shouldContainExactly(it)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
```

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.scanner.ScanResultTest.save*'`
Expected: PASS already. This is a characterization test: the old code also ends with one file in the directory. That the temp
file now sits next to the target is checked by reading the diff in Step 3.

- [ ] **Step 2: Write the temp file next to the target**

In `save`, add the import `java.nio.file.StandardCopyOption` and replace the two lines that create and move the temp file:

```kotlin
        val content = format.encodeToString(this)
        val tempFile = createTempFile(file.toAbsolutePath().parent, file.name, ".tmp")
        tempFile.writeText(content)
        tempFile.moveTo(file, StandardCopyOption.ATOMIC_MOVE)
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.scanner.ScanResultTest'`
Expected: PASS. Read the diff: the temp file's parent is `file.toAbsolutePath().parent`, not the system temp directory.

- [ ] **Step 4: Commit**

```bash
git add src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt src/jvmTest/kotlin/com/bkahlert/netmon/scanner/ScanResultTest.kt
git commit -m "fix(scanner): replace the state file atomically"
```

---

### Task 4: A returning network gets a new scanner

**Files:**
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/Application.kt` (`finalize`)

**Interfaces:**
- Consumes: the `scanners` map in `Application.start()`.

No unit test: `NmapNetworkScanner` needs the nmap binary, so `Application`'s wiring has none (see the spec). The check is
reading the diff plus Step 2.

- [ ] **Step 1: Drop the scanner with its cache**

In `Application.start()`, in the `finalize` lambda, insert as its first statement:

```kotlin
                scanners.remove(interfaceAddress.address)
```

so the lambda begins:

```kotlin
            finalize = { (interfaceAddress, interfaceName) ->
                scanners.remove(interfaceAddress.address)
                serviceInfoCaches.remove(interfaceAddress.address)?.also {
```

- [ ] **Step 2: Run everything the change can touch**

Run: `make test-jvm`
Expected: PASS.

Run: `make test-js`
Expected: PASS (`Host` is shared with the display).

- [ ] **Step 3: Commit**

```bash
git add src/jvmMain/kotlin/com/bkahlert/netmon/Application.kt
git commit -m "fix(scanner): start a returning network with a new scanner"
```
