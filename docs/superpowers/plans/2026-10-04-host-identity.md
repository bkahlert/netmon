# Host identity and model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A host is followed by its MAC address across IP changes, and Apple devices that hide their model in mDNS get it from iOS `lockdownd`.

**Architecture:** `Host` gains `mac`, read from nmap's XML. `ScanResult.merge` pairs recorded and scanned hosts by MAC first, then by IP, and drops a recorded host whose IP another device took. A new `LockdownModelEnricher` asks TCP 62078 for `ProductType` and caches the answer itself. The display shows the end of the MAC for a host with neither name nor model.

**Tech Stack:** Kotlin Multiplatform (JVM scanner, JS display with fritz2), kotlinx.serialization, kotlin.test with Kotest matchers, Gradle (`make test-jvm`, `make test-js`).

**Spec:** [2026-10-04-host-identity-design.md](../specs/2026-10-04-host-identity-design.md)

## Global Constraints

- Keep Kotlin and nmap. No new dependency.
- No manual override table for models or names; the model comes from the network.
- `mac` is optional on the wire (`explicitNulls = false`, `ignoreUnknownKeys = true`); scanner and display may differ in version.
- MACs are lowercase with colons (`dc:a6:32:a5:ba:b6`).
- Probe: connect timeout 1 s, read timeout 2 s, success cached 24 hours, failure cached 5 minutes, key is the MAC (the IP string without one).
- Candidates for the probe: no model, and vendor `null` or starting with "Apple".
- A host with a probed model and no vendor gets the vendor "Apple Inc.".
- No two hosts in a merged list share an IP.
- Gradle: one build at a time, in the foreground. Test commands: `make test-jvm`, `make test-js`.
- Commits are Conventional Commits, scope `scanner` or `display`, one change per commit, header at most 72 characters. Branch `feat/host-identity-and-model` (already created from `origin/main`).
- Tests follow the file they extend (`kotlin.test` `@Test` with Kotest matchers), tests first, helpers last, no comments in tests.

## Review Focus

- A new device takes a recorded device's IP while the old one is still inside the 3-minute grace: the new host inherits nothing and the old one is gone (Task 3).
- Two devices swap IPs between scans: each keeps its own name, model and `since` (Task 3).
- An unprivileged scan (no MACs) meets a state file written with MACs, and the reverse: no duplicate host, no lost MAC (Task 3).
- A device that opens port 62078 and sends garbage, a huge length or hangs: the probe returns `null` within its timeouts and allocates nothing large (Task 4).
- A reply with a `DOCTYPE` pointing at an external DTD is not fetched (Task 4).

## File Structure

| File | Change |
|---|---|
| `src/commonMain/.../Host.kt` | add `mac` |
| `src/jvmMain/.../nmap/NmapXml.kt` | read `mac`; use `SecureXml` |
| `src/jvmMain/.../xml/SecureXml.kt` | new: the hardened `XMLStreamReader` factory, shared by nmap and lockdown |
| `src/jvmMain/.../enrichment/HostEnricher.kt` | `copy` learns `mac` |
| `src/jvmMain/.../scanner/ScanResult.kt` | MAC-aware pairing in `merge` |
| `src/jvmMain/.../enrichment/LockdownModelEnricher.kt` | new: the probe and its cache |
| `src/jvmMain/.../Application.kt` | add the enricher to the chain |
| `src/jsMain/.../ui/network.kt` | MAC caption fallback |
| tests | `HostTest` (helper and round trip), `NmapXmlTest`, `HostPropertyEnricherTest` (new), `ScanResultTest`, `LockdownModelEnricherTest` (new), `NetworkKtTest` |

All paths below are relative to the repository root `/Users/bkahlert/Development/com.bkahlert/netmon`; `...` stands for `com/bkahlert/netmon`.

---

### Task 1: Gate: does JmDNS map the iPads to their IPv4?

The spec leaves this unverified. If `Rabban.local` does not map to an IPv4, fixing the mapping comes before everything else and this plan stops here. This task leaves nothing in git.

**Files:**
- Create (temporary, never committed): `src/jvmTest/kotlin/com/bkahlert/netmon/mdns/TempMdnsProbeTest.kt`

- [ ] **Step 1: Write the temporary probe**

```kotlin
package com.bkahlert.netmon.mdns

import java.net.InetAddress
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test

class TempMdnsProbeTest {

    @Test
    fun dump() {
        val address = System.getenv("NETMON_PROBE_ADDR") ?: return
        val jmDns = JmDNS(InetAddress.getByName(address), "probe")
        val cache = JmDNSServiceInfoCache(jmDns, serviceTypes = emptyArray())
        Thread.sleep(30_000)
        Paths.get("build/tmp/mdns-probe.txt").apply { parent.createDirectories() }.writeText(cache.toString())
        cache.close()
        jmDns.close()
    }
}
```

- [ ] **Step 2: Run it with this Mac's LAN address**

```shell
export NETMON_PROBE_ADDR=$(ipconfig getifaddr en0)
./gradlew --no-daemon --console=plain cleanJvmTest jvmTest --tests '*TempMdnsProbeTest'
grep -io '[0-9.]*=\[[^]]*\]@\[[^]]*\(rabban\|feyd\)[^]]*\]' build/tmp/mdns-probe.txt
```

Expected: one line per iPad, shaped `192.168.x.y=[companion-link,...]@[Rabban.local.]`, with an IPv4 on the left. Write both IPv4 addresses down for Task 6.

If an iPad shows up only with IPv6 addresses or not at all: stop, delete the temporary file and tell the user. Do not continue.

- [ ] **Step 3: Delete the temporary probe**

```shell
rm src/jvmTest/kotlin/com/bkahlert/netmon/mdns/TempMdnsProbeTest.kt
git status --short
```

Expected: no output (the tree is clean apart from the untracked `.claude/`).

---

### Task 2: `Host.mac`, read from nmap

**Files:**
- Modify: `src/commonMain/kotlin/com/bkahlert/netmon/Host.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/nmap/NmapXml.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/enrichment/HostEnricher.kt`
- Test: `src/commonTest/kotlin/com/bkahlert/netmon/HostTest.kt`, `src/jvmTest/kotlin/com/bkahlert/netmon/nmap/NmapXmlTest.kt`
- Create: `src/jvmTest/kotlin/com/bkahlert/netmon/enrichment/HostPropertyEnricherTest.kt`

**Interfaces:**
- Produces: `Host.mac: String?` (last constructor parameter, `@SerialName("mac")`); test helper `Host(ip: String = ..., ..., lastSeen: Instant? = null, mac: String? = null)` (`Host.Companion.invoke` in `HostTest.kt`); `HostPropertyEnricher.copy(Host::mac, value)`.

- [ ] **Step 1: Write the failing tests**

In `HostTest.kt` add the test (after `last_seen_round_trips_as_epoch_seconds`) and the helper parameter:

```kotlin
    @Test
    fun mac_round_trips() {
        val host = Host(ip = IP.of("10.0.0.1"), status = Status.UP, mac = "dc:a6:32:a5:ba:b6")

        val json = JsonFormat.encodeToString(Host.serializer(), host)

        json shouldContain "\"mac\": \"dc:a6:32:a5:ba:b6\""
        JsonFormat.decodeFromString(Host.serializer(), json) shouldBe host
    }
```

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
    mac: String? = null,
) = Host(
    ip = IP.of(ip),
    name = name,
    status = status,
    since = since,
    model = model,
    vendor = vendor,
    services = services,
    lastSeen = lastSeen,
    mac = mac,
)
```

In `NmapXmlTest.kt` replace the first test and add one, plus the fixture at the bottom:

```kotlin
    @Test
    fun hosts_with_ip_name_status_vendor_and_mac() {
        val result = NmapXml.parse(nmapRun(UP_WITH_NAME, UP_WITHOUT_NAME, LOCALHOST))

        result.shouldContainExactly(
            Host(ip = IP.of("192.168.42.180"), name = "foo.bar", status = Status.UP, vendor = "Raspberry Pi Trading", mac = "dc:a6:32:a5:ba:b6"),
            Host(ip = IP.of("192.168.42.190"), name = null, status = Status.UP, vendor = "Raspberry Pi Trading", mac = "e4:5f:01:34:81:39"),
            Host(ip = IP.of("192.168.42.33"), name = null, status = Status.UP, vendor = null, mac = null),
        )
    }

    @Test
    fun a_private_mac_has_no_vendor_but_is_read() {
        val result = NmapXml.parse(nmapRun(PRIVATE_MAC))

        result.single() shouldBe Host(ip = IP.of("192.168.42.9"), name = null, status = Status.UP, vendor = null, mac = "de:ad:be:ef:00:01")
    }
```

```kotlin
private val PRIVATE_MAC = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.9" addrtype="ipv4"/>
    <address addr="DE:AD:BE:EF:00:01" addrtype="mac"/>
    </host>
""".trimIndent()
```

Create `HostPropertyEnricherTest.kt`:

```kotlin
package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.invoke
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HostPropertyEnricherTest {

    @Test
    fun copy_sets_the_mac() {
        val host = Host(mac = null)

        val result = with(HostPropertyEnricher) { host.copy(Host::mac, "aa:bb:cc:dd:ee:ff") }

        result shouldBe host.copy(mac = "aa:bb:cc:dd:ee:ff")
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `make test-jvm`
Expected: FAIL to compile, `No parameter with name 'mac' found` (or unresolved `mac`).

- [ ] **Step 3: Implement**

`Host.kt`, as the last parameter:

```kotlin
    /** The time of the last scan that found the host up. */
    @SerialName("lastSeen") @Serializable(InstantAsEpochSecondsSerializer::class) val lastSeen: Instant? = null,
    /** The hardware address, lowercase with colons; it identifies the device across IP changes. */
    @SerialName("mac") val mac: String? = null,
) {
```

`NmapXml.kt`, update the KDoc, the local variable, the `mac` branch and the constructor call:

```kotlin
     * A host without an IPv4 or IPv6 address is left out. The name is the first `hostname` element's name; the vendor
     * and the MAC (lowercase) come from the MAC address element. Each is `null` when absent.
```

```kotlin
        var vendor: String? = null
        var mac: String? = null
```

```kotlin
                            "mac" -> {
                                vendor = getAttributeValue(null, "vendor")
                                mac = getAttributeValue(null, "addr")?.lowercase()
                            }
```

```kotlin
        return address?.let { Host(ip = IP.of(it), name = name, status = state?.let(Status::of), vendor = vendor, mac = mac) }
```

`HostEnricher.kt`, inside `copy` after `services`:

```kotlin
            mac = if (property == Host::mac && value is String) value else mac,
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `make test-jvm`
Expected: PASS (the JVM unit tests, `commonTest` included).

- [ ] **Step 5: Commit**

```shell
git add src/commonMain src/jvmMain src/commonTest src/jvmTest
git commit -m "feat(scanner): read a host's MAC address"
```

---

### Task 3: Pair hosts by MAC in `merge`

**Files:**
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt`
- Test: `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/ScanResultTest.kt`

**Interfaces:**
- Consumes: `Host.mac`, the test helper `Host(..., mac = ...)` from Task 2; the file's own `scanAt`, `mergedWith` and `Merged` helpers (`Merged.host` is the single merged host, `Merged.result` the whole `ScanResult`, `Merged.changed` the `onChange` calls).
- Produces: `ScanResult.merge` with unchanged signature; behavior per the spec's "Matching in `merge`".

- [ ] **Step 1: Write the failing tests**

Add to `ScanResultTest` before the closing brace of the class (the helpers stay at the bottom of the file):

```kotlin
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
```

Add `import com.bkahlert.netmon.IP` to the imports of the file (after `Host`).

- [ ] **Step 2: Run the tests to see them fail**

Run: `make test-jvm`
Expected: FAIL. `merge_host_that_moved_...` (two hosts instead of one), `merge_new_device_on_a_taken_ip_...` (the new host inherits name and model), `merge_hosts_that_swapped_ips_...` (names swapped), and the two no-MAC tests fail on the missing `mac`; `merge_host_unseen_at_an_ip_nobody_took_...` already passes.

- [ ] **Step 3: Implement the pairing**

In `ScanResult.kt` replace the `hosts = buildSet { ... }.sorted().map { ip -> ... }` block of `merge`:

```kotlin
            hosts = pair(hosts, currentResult.hosts)
                .map { (recordedHost, scannedHost) ->
                    val mergedHost = mergeHost(recordedHost, scannedHost, currentResult.timestamp, downAfter, notBefore)
                    if (recordedHost == null || recordedHost.status != mergedHost.status) onChange(mergedHost)
                    mergedHost
                }
                .sortedBy { it.ip },
```

In `mergeHost` use the shared predicate and carry the MAC:

```kotlin
        scanned != null && scanned.seenUp -> scanned.copy(
            name = scanned.name ?: recorded?.name,
            status = Status.UP,
            since = if (recorded != null && recorded.status == Status.UP) recorded.since ?: scanTime else scanTime,
            lastSeen = scanTime,
            model = scanned.model ?: recorded?.model,
            vendor = scanned.vendor ?: recorded?.vendor,
            services = scanned.services ?: recorded?.services,
            mac = scanned.mac ?: recorded?.mac,
        )
```

Add below `mergeHost`:

```kotlin
    /**
     * Pairs each scanned host with the recorded host it is, and each recorded host the scan did not match with `null`.
     *
     * A recorded host that no scanned host matched is left out when a scanned host that is up holds its IP:
     * another device took it over, and the list must not hold two hosts with one IP.
     */
    private fun pair(recorded: List<Host>, scanned: List<Host>): List<Pair<Host?, Host?>> {
        val unmatchedRecorded = recorded.toMutableList()
        val pairs = mutableListOf<Pair<Host?, Host?>>()
        val unmatchedScanned = mutableListOf<Host>()

        scanned.forEach { host ->
            val index = host.mac?.let { mac -> unmatchedRecorded.indexOfFirst { it.mac == mac } } ?: -1
            if (index >= 0) pairs += unmatchedRecorded.removeAt(index) to host else unmatchedScanned += host
        }
        unmatchedScanned.forEach { host ->
            val index = unmatchedRecorded.indexOfFirst { it.ip == host.ip && (it.mac == null || host.mac == null) }
            pairs += (if (index >= 0) unmatchedRecorded.removeAt(index) else null) to host
        }

        val takenIps = scanned.filter { it.seenUp }.map { it.ip }.toSet()
        unmatchedRecorded.filter { it.ip !in takenIps }.forEach { pairs += it to null }
        return pairs
    }

    private val Host.seenUp: Boolean get() = status == null || status == Status.UP
```

Remove the `// TODO improve detection...` comment (it sat on the deleted line). The `Instant` and `Status` imports stay in use.

- [ ] **Step 4: Run the tests to see them pass**

Run: `make test-jvm`
Expected: PASS, including every existing `ScanResultTest` case.

- [ ] **Step 5: Commit**

```shell
git add src/jvmMain src/jvmTest
git commit -m "feat(scanner): follow a host by its MAC address" \
  -m "A host that changes IP stays one host. A device that takes over a recorded host's IP starts a new record and replaces the old one."
```

---

### Task 4: Read an iPad's model from lockdownd

**Files:**
- Create: `src/jvmMain/kotlin/com/bkahlert/netmon/xml/SecureXml.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/nmap/NmapXml.kt`
- Create: `src/jvmMain/kotlin/com/bkahlert/netmon/enrichment/LockdownModelEnricher.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/Application.kt`
- Test: `src/jvmTest/kotlin/com/bkahlert/netmon/enrichment/LockdownModelEnricherTest.kt` (new)

**Interfaces:**
- Consumes: `Host.mac`, `Host.copy(model=, vendor=)`, `HostEnricher` (= `Enricher<Host>`: `fun enrich(entity: Host): Host?`), `IP.bytes`.
- Produces: `internal object SecureXml { fun reader(xml: String): XMLStreamReader }`; `class LockdownModelEnricher(port: Int = LOCKDOWN_PORT, clock: Clock = Clock.System, connectTimeout: Duration = 1.seconds, readTimeout: Duration = 2.seconds) : HostEnricher` with `companion object { const val LOCKDOWN_PORT = 62078 }`.

#### Part A: share the XML reader

- [ ] **Step 1: Extract `SecureXml`**

Create `src/jvmMain/kotlin/com/bkahlert/netmon/xml/SecureXml.kt`:

```kotlin
package com.bkahlert.netmon.xml

import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader

/** An XML reader that resolves neither DTDs nor external entities. */
internal object SecureXml {

    private val factory: XMLInputFactory = XMLInputFactory.newDefaultFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    }

    fun reader(xml: String): XMLStreamReader = factory.createXMLStreamReader(xml.reader())
}
```

In `NmapXml.kt` delete the `factory` property and the imports `javax.xml.XMLConstants` and `javax.xml.stream.XMLInputFactory`, add `import com.bkahlert.netmon.xml.SecureXml`, and change the first line of `parse`:

```kotlin
        val reader = SecureXml.reader(xml)
```

- [ ] **Step 2: Run the nmap tests**

Run: `make test-jvm`
Expected: PASS (`NmapXmlTest.the_doctype_is_not_resolved` still holds).

- [ ] **Step 3: Commit**

```shell
git add src/jvmMain
git commit -m "refactor(scanner): share the XML reader that resolves no DTD"
```

#### Part B: the enricher

- [ ] **Step 4: Write the failing tests**

Create `src/jvmTest/kotlin/com/bkahlert/netmon/enrichment/LockdownModelEnricherTest.kt`:

```kotlin
package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.epoch
import com.bkahlert.netmon.invoke
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LockdownModelEnricherTest {

    @Test
    fun a_host_without_vendor_gets_the_model_and_apple_as_vendor() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate())

            result shouldBe candidate().copy(model = "iPad7,5", vendor = "Apple Inc.")
        }
    }

    @Test
    fun a_host_with_an_apple_vendor_keeps_it() {
        FakeLockdownd(reply = plist("iPad8,3")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate(vendor = "Apple"))

            result shouldBe candidate(vendor = "Apple").copy(model = "iPad8,3")
        }
    }

    @Test
    fun the_request_asks_for_the_product_type() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            LockdownModelEnricher(port = server.port).enrich(candidate())

            server.requests.single() should {
                it shouldContain "<key>Request</key><string>GetValue</string>"
                it shouldContain "<key>Key</key><string>ProductType</string>"
            }
        }
    }

    @Test
    fun a_known_model_is_not_asked_again_within_a_day() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, clock = TestClock())

            val first = enricher.enrich(candidate())
            val second = enricher.enrich(candidate())

            first?.model shouldBe "iPad7,5"
            second?.model shouldBe "iPad7,5"
            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun a_known_model_is_asked_again_after_a_day() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val clock = TestClock()
            val enricher = LockdownModelEnricher(port = server.port, clock = clock)
            enricher.enrich(candidate())

            clock.advance(24.hours + 1.seconds)
            enricher.enrich(candidate())

            server.requests shouldHaveSize 2
        }
    }

    @Test
    fun a_device_that_moved_is_found_in_the_cache_by_its_mac() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, clock = TestClock())
            enricher.enrich(candidate(ip = "127.0.0.1"))

            val result = enricher.enrich(candidate(ip = "127.0.0.2"))

            result?.model shouldBe "iPad7,5"
            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun without_a_mac_the_ip_is_the_cache_key() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, clock = TestClock())

            enricher.enrich(candidate(mac = null))
            enricher.enrich(candidate(mac = null))

            server.requests shouldHaveSize 1
        }
    }

    @Test
    fun a_failed_probe_is_retried_after_five_minutes() {
        FakeLockdownd(reply = errorReply("GetProhibited")).use { server ->
            val clock = TestClock()
            val enricher = LockdownModelEnricher(port = server.port, clock = clock)

            val first = enricher.enrich(candidate())
            clock.advance(4.minutes)
            enricher.enrich(candidate())
            val requestsBeforeRetry = server.requests.size
            clock.advance(1.minutes + 1.seconds)
            enricher.enrich(candidate())

            first.shouldBeNull()
            requestsBeforeRetry shouldBe 1
            server.requests shouldHaveSize 2
        }
    }

    @Test
    fun a_host_with_another_vendor_is_not_probed() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate(vendor = "Raspberry Pi Trading"))

            result.shouldBeNull()
            server.requests.shouldBeEmpty()
        }
    }

    @Test
    fun a_host_with_a_model_is_not_probed() {
        FakeLockdownd(reply = plist("iPad7,5")).use { server ->
            val result = LockdownModelEnricher(port = server.port).enrich(candidate(model = "iPad8,3"))

            result.shouldBeNull()
            server.requests.shouldBeEmpty()
        }
    }

    @Test
    fun a_closed_port_yields_nothing() {
        val closedPort = ServerSocket(0).use { it.localPort }

        LockdownModelEnricher(port = closedPort).enrich(candidate()).shouldBeNull()
    }

    @Test
    fun a_device_that_never_answers_yields_nothing_after_the_read_timeout() {
        FakeLockdownd(reply = null).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, readTimeout = 200.milliseconds)

            enricher.enrich(candidate()).shouldBeNull()
        }
    }

    @Test
    fun an_absurd_reply_length_yields_nothing() {
        FakeLockdownd(reply = ByteBuffer.allocate(4).putInt(Int.MAX_VALUE).array()).use { server ->
            val enricher = LockdownModelEnricher(port = server.port, readTimeout = 200.milliseconds)

            enricher.enrich(candidate()).shouldBeNull()
        }
    }

    @Test
    fun a_reply_that_is_not_a_plist_yields_nothing() {
        FakeLockdownd(reply = framed("not xml at all")).use { server ->
            LockdownModelEnricher(port = server.port).enrich(candidate()).shouldBeNull()
        }
    }

    @Test
    fun an_external_dtd_is_not_fetched() {
        val body = """<?xml version="1.0"?><!DOCTYPE plist SYSTEM "file:///nonexistent/PropertyList.dtd">
            |<plist version="1.0"><dict><key>Value</key><string>iPad7,5</string></dict></plist>""".trimMargin()
        FakeLockdownd(reply = framed(body)).use { server ->
            LockdownModelEnricher(port = server.port).enrich(candidate())?.model shouldBe "iPad7,5"
        }
    }
}

private fun candidate(
    ip: String = "127.0.0.1",
    mac: String? = "aa:bb:cc:dd:ee:01",
    vendor: String? = null,
    model: String? = null,
) = Host(ip = ip, name = null, model = model, vendor = vendor, services = null, mac = mac)

private fun framed(body: String): ByteArray = body.toByteArray().let { ByteBuffer.allocate(4 + it.size).putInt(it.size).put(it).array() }

private fun plist(value: String): ByteArray = framed(
    """<?xml version="1.0" encoding="UTF-8"?>
    |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    |<plist version="1.0"><dict><key>Key</key><string>ProductType</string><key>Request</key><string>GetValue</string><key>Value</key><string>$value</string></dict></plist>""".trimMargin(),
)

private fun errorReply(error: String): ByteArray = framed(
    """<?xml version="1.0" encoding="UTF-8"?>
    |<plist version="1.0"><dict><key>Error</key><string>$error</string><key>Request</key><string>GetValue</string></dict></plist>""".trimMargin(),
)

private class TestClock(private var current: Instant = 0.epoch) : Clock {
    override fun now(): Instant = current
    fun advance(by: kotlin.time.Duration) {
        current += by
    }
}

private class FakeLockdownd(private val reply: ByteArray?) : AutoCloseable {

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val sockets = CopyOnWriteArrayList<Socket>()
    val requests = CopyOnWriteArrayList<String>()
    val port: Int get() = server.localPort

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    return@thread
                }
                sockets += socket
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket) {
        try {
            val input = DataInputStream(socket.getInputStream())
            requests += String(ByteArray(input.readInt()).also(input::readFully))
            reply?.let { socket.getOutputStream().apply { write(it); flush() } }
        } catch (e: IOException) {
            // the client left
        }
    }

    override fun close() {
        server.close()
        sockets.forEach { it.close() }
    }
}
```

- [ ] **Step 5: Run the tests to see them fail**

Run: `make test-jvm`
Expected: FAIL to compile, `Unresolved reference 'LockdownModelEnricher'`.

- [ ] **Step 6: Implement the enricher**

Create `src/jvmMain/kotlin/com/bkahlert/netmon/enrichment/LockdownModelEnricher.kt`:

```kotlin
package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.xml.SecureXml
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.xml.stream.XMLStreamConstants
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * An enricher that reads the model of an Apple mobile device from iOS `lockdownd` on TCP port [LOCKDOWN_PORT].
 *
 * The device answers `GetValue` for `ProductType` (for example `iPad7,5`) without pairing. The protocol is undocumented.
 * Only a [Host] without a model whose vendor is unknown or Apple is probed. An answer is remembered for a day, a failure
 * for five minutes, per MAC address (per IP without one).
 */
class LockdownModelEnricher(
    private val port: Int = LOCKDOWN_PORT,
    private val clock: Clock = Clock.System,
    private val connectTimeout: Duration = 1.seconds,
    private val readTimeout: Duration = 2.seconds,
) : HostEnricher {

    private val logger by SLF4J

    private class Probed(val model: String?, val expiresAt: Instant)

    private val cache = ConcurrentHashMap<String, Probed>()

    override fun enrich(entity: Host): Host? {
        if (entity.model != null || !(entity.vendor == null || entity.vendor.startsWith("Apple", ignoreCase = true))) return null
        val now = clock.now()
        val key = entity.mac ?: entity.ip.toString()
        val probed = cache[key]?.takeIf { it.expiresAt > now } ?: run {
            val model = probe(entity.ip.bytes)
            Probed(model, now + if (model != null) SUCCESS_TTL else FAILURE_TTL).also { cache[key] = it }
        }
        val model = probed.model ?: return null
        return entity.copy(model = model, vendor = entity.vendor ?: "Apple Inc.")
            .also { logger.info("{} enriched: model={}", it, model) }
    }

    private fun probe(address: ByteArray): String? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByAddress(address), port), connectTimeout.inWholeMilliseconds.toInt())
            socket.soTimeout = readTimeout.inWholeMilliseconds.toInt()
            val request = REQUEST.toByteArray()
            DataOutputStream(socket.getOutputStream()).apply {
                writeInt(request.size)
                write(request)
                flush()
            }
            val input = DataInputStream(socket.getInputStream())
            val length = input.readInt()
            require(length in 1..MAX_REPLY_BYTES) { "Reply length $length" }
            valueOf(String(ByteArray(length).also(input::readFully)))
        }
    } catch (e: InterruptedException) {
        throw e
    } catch (e: Exception) {
        logger.debug("No model from {}:{}: {}", InetAddress.getByAddress(address).hostAddress, port, e.toString())
        null
    }

    private fun valueOf(plist: String): String? {
        val reader = SecureXml.reader(plist)
        try {
            var afterValueKey = false
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    when (reader.localName) {
                        "key" -> afterValueKey = reader.elementText == "Value"
                        "string" -> if (afterValueKey) return reader.elementText.takeIf { it.isNotBlank() }
                        else -> afterValueKey = false
                    }
                }
            }
            return null
        } finally {
            reader.close()
        }
    }

    override fun toString(): String = this::class.simpleName ?: "<object>"

    companion object {
        const val LOCKDOWN_PORT = 62078
        private const val MAX_REPLY_BYTES = 64 * 1024
        private val SUCCESS_TTL = 24.hours
        private val FAILURE_TTL = 5.minutes
        private const val REQUEST = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>Request</key><string>GetValue</string><key>Key</key><string>ProductType</string><key>Label</key><string>netmon</string></dict></plist>"""
    }
}
```

Wire it in `Application.kt`: add the import `com.bkahlert.netmon.enrichment.LockdownModelEnricher` (alphabetical, after `HostServicesEnricher`) and the enricher after `AppleHostEnricher`:

```kotlin
                            AppleHostEnricher(serviceInfoCache, DeviceModelCodes.load(DeviceModelCodes.resource)),
                            LockdownModelEnricher(),
                            HostServicesEnricher(serviceInfoCache),
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `make test-jvm`
Expected: PASS. If `a_device_that_moved_is_found_in_the_cache_by_its_mac` fails, the key is not the MAC; fix `enrich`, do not change the test.

- [ ] **Step 8: Commit**

```shell
git add src/jvmMain src/jvmTest
git commit -m "feat(scanner): read an iPad's model from lockdownd" \
  -m "The iPads advertise no model over mDNS. iOS answers ProductType on TCP 62078 without pairing, so a host without a model and with an unknown or Apple vendor is asked once a day, or every five minutes after a failure."
```

---

### Task 5: Show the end of the MAC for a host with neither name nor model

**Files:**
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/ui/network.kt`
- Test: `src/jsTest/kotlin/com/bkahlert/netmon/ui/NetworkKtTest.kt`

**Interfaces:**
- Consumes: `Host.mac` (Task 2).

- [ ] **Step 1: Write the failing tests**

Add to `NetworkKtTest` after `a_host_without_name_and_vendor_fits_the_placeholders` (the file already imports `shouldContain`; add `import io.kotest.matchers.string.shouldNotContain`):

```kotlin
    @Test
    fun a_host_without_name_and_model_shows_the_end_of_its_mac() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), mac = "dc:a6:32:a5:ba:b6", status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("a5:ba:b6") shouldContain "a5:ba:b6"
        container.remove()
    }

    @Test
    fun a_host_without_name_model_and_mac_shows_the_placeholder() = runTest {
        val now = Clock.System.now()
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now)), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("❔") shouldContain "❔"
        container.remove()
    }

    @Test
    fun a_named_host_does_not_show_its_mac() = runTest {
        val now = Clock.System.now()
        val named = Host(ip = IP.of("192.168.1.1"), name = "printer.local.", mac = "dc:a6:32:a5:ba:b6", status = Status.UP, since = now)
        val store = RootStore(listOf(named), job = job)

        val container = rendered { hosts(store, clock = MutableStateFlow(now)) }

        container.textOnce("printer") shouldNotContain "a5:ba:b6"
        container.remove()
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `make test-js`
Expected: FAIL: `a_host_without_name_and_model_shows_the_end_of_its_mac` times out waiting for text `a5:ba:b6` (the other two pass already).

- [ ] **Step 3: Implement**

In `network.kt`, next to `vendors`:

```kotlin
    val macs = host.data.map { it.mac }.distinctUntilChanged()
```

and replace the `captions` line:

```kotlin
    val captions = combine(hostNames, modelNames, macs) { h, m, mac -> h?.substringBefore(".") ?: m ?: mac?.takeLast(8) }
```

`combine` is already imported from `kotlinx.coroutines.flow`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `make test-js`
Expected: PASS, including `a_host_without_name_and_vendor_fits_the_placeholders` (its host has no MAC).

- [ ] **Step 5: Commit**

```shell
git add src/jsMain src/jsTest
git commit -m "feat(display): show the end of a nameless host's MAC address"
```

---

### Task 6: Verify on the LAN

Nothing from this task is committed.

**Files:**
- Create (temporary): `src/jvmTest/kotlin/com/bkahlert/netmon/enrichment/TempLockdownProbeTest.kt`

- [ ] **Step 1: Write the temporary probe**

```kotlin
package com.bkahlert.netmon.enrichment

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.invoke
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test

class TempLockdownProbeTest {

    @Test
    fun probe() {
        val ips = System.getenv("NETMON_PROBE_IPS")?.split(",") ?: return
        val enricher = LockdownModelEnricher()
        val lines = ips.map { ip ->
            "$ip ${enricher.enrich(Host(ip = ip, name = null, model = null, vendor = null, services = null))?.model}"
        }
        Paths.get("build/tmp/lockdown-probe.txt").apply { parent.createDirectories() }.writeText(lines.joinToString("\n"))
    }
}
```

- [ ] **Step 2: Run it with the iPads' addresses from Task 1**

```shell
export NETMON_PROBE_IPS=192.168.x.y,192.168.x.z   # Rabban, Feyd
./gradlew --no-daemon --console=plain cleanJvmTest jvmTest --tests '*TempLockdownProbeTest'
cat build/tmp/lockdown-probe.txt
```

Expected: `<Rabban's IP> iPad7,5` and `<Feyd's IP> iPad8,3`. A `null` means the device did not answer (asleep or the port closed): wake it and run again before suspecting the code.

- [ ] **Step 3: Confirm the display maps the codes**

```shell
grep -c '"iPad7,5"\|"iPad8,3"' src/commonMain/resources/assets/device-model-codes.json
```

Expected: `2`.

- [ ] **Step 4: Delete the temporary probe and run the full unit suites**

```shell
rm src/jvmTest/kotlin/com/bkahlert/netmon/enrichment/TempLockdownProbeTest.kt
make test-jvm
make test-js
git status --short
```

Expected: both suites PASS; `git status` shows nothing but the untracked `.claude/`.
