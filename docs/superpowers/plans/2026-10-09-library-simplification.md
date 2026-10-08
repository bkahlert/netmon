# Library Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Remove unused catalogs and replace generic lifecycle, IP, and SSDP code.

**Architecture:** Deliver four independent changes behind existing domain
interfaces. Prototype jUPnP before replacing the current discovery backend.
Keep ownership and input-boundary protections in application adapters.

**Tech Stack:** Kotlin 2.4.20, JDK 17, coroutines 1.11.0, ipaddr.js 2.5.0,
candidate jUPnP 3.0.5, Gradle, Kotest/JUnit, Chrome, GraalVM arm64.

**Spec:** [Library Simplification Design](../specs/2026-10-09-library-simplification-design.md)

## Global Constraints

- Keep Kotlin 2.4.20, JDK 17, and existing coroutines 1.11.0.
- Keep the arm64 GraalVM native scanner; JVM compilation alone is insufficient.
- Keep native heap `64m` and scanner service `MemoryMax=128M`.
- Preserve scan order, network selection, pause duration, topics, and payloads.
- Stop and join workers before closing sessions or the shared publisher.
- Preserve `IP`, `Cidr`, serialization, filenames, and identity lookup interfaces.
- Reject cross-host descriptor URLs before HTTP; never resolve LOCATION hostnames.
- Limit descriptor bodies to 262144 bytes, with 3-second header and body limits.
- Keep HTTP descriptor retrieval redirect-free and XML external entities disabled.
- Keep discovery isolated per network session; do not set global interface properties.
- Do not implement or commit changes until the plan is reviewed.

## Review Focus

- Concurrent start and shutdown: exactly one worker lifetime and stable results.
  Covered by Task 3's concurrent lifecycle tests.
- Cancellation during a blocked pipe read: child and drainers must exit.
  Covered by Task 2's subprocess cancellation test.
- IPv6 zero prefixes and mapped dotted forms: correct byte length and IPv4 mapping.
  Covered by Task 4's codec regression vectors.
- One sender advertising two roots: goodbye removes only its target.
  Covered by Task 5's same-IP registry test.
- Two sessions retrieving the same URL: neither may miss the device.
  Covered by Task 5's concurrent-session prototype test.

## Execution boundaries

Recommended order: Task 1, Task 4, Tasks 2-3, then Tasks 5-6.
Tasks 1 and 4 are independent. Task 3 depends on Task 2.
Task 6 depends on a successful Task 5; no other task depends on jUPnP.
Use an isolated execution branch/worktree after approval.
Never stage unrelated files. Each completed delivery gets its own reviewed commit.
Run independent tests separately from builds; never run Gradle builds concurrently.

Paths below are relative to the repository root.
Future files are marked **Create** and are not existing source references.

### Task 1: Remove unused Heroicons catalogs

**Files:** Delete the four catalogs listed in the
[icon spec](../specs/2026-10-09-library-simplification-design.md#1-unused-icon-catalogs).
Do not modify other icon files or dependency manifests.

**Interfaces:** Consumes no library. Produces the existing SVG/Iconify/SF Symbols
interfaces unchanged, without exported unused Heroicons catalogs.

- [ ] **Step 1: Check deletion preconditions.**
  Search main and test sources for the four catalog names.
  Expected: only declarations and internal catalog references.
  If new live references exist, stop deletion and revise this delivery.
- [ ] **Step 2: Delete only the four catalog files.**
  No failing-test ceremony is needed for unused-code removal.
- [ ] **Step 3: Verify browser behavior and distribution.**
  Run `make test-js`, then `./gradlew jsBrowserDistribution`, then
  `make test-layout`, sequentially.
  Expected: browser and layout tests pass; distribution builds without missing icons.
  Confirm the diff removes 2717 catalog lines and retains all live icon rendering.
- [ ] **Step 4: Review and commit only the four deletions.**
  Suggested message: `refactor(display): remove unused Heroicons catalogs`.

### Task 2: Make subprocess ownership cancellation-safe

**Files:** Modify
[CommandLine.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/support/exec/CommandLine.kt)
and
[CommandLineTest.kt](../../../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/support/exec/CommandLineTest.kt).

**Interfaces:** Consumes the existing `CommandLine(command, arguments)`.
Produces the unchanged `fun exec(): CommandLine.Exit`, with interruption releasing
the owned process and readers before throwing `InterruptedException`.

- [ ] **Step 1: Add `interruption_reaps_child_while_stdout_is_open`.**
  Run `sh -c 'echo $$ > "$1"; exec sleep 60' fixture /absolute/test/pid`.
  Supply the actual temporary PID-file path as the last argument.
  Synchronize readiness through that file, while stdout remains open.
  Interrupt the thread executing `exec()` after the PID appears.
  Assert completion within 5 seconds, propagated interruption, a dead child PID,
  and no live stdout/stderr drainer belonging to that invocation.
  Clean the exact temporary file and child in test cleanup, even on failure.
- [ ] **Step 2: Run the test before fixing ownership.**
  Run `./gradlew jvmTest --tests '*CommandLineTest'`.
  Expected: the new test fails because stdout reading prevents prompt interruption;
  existing output and exit-code tests pass.
- [ ] **Step 3: Keep pipe reads off the waiting caller.**
  Drain stdout and stderr on owned reader threads while the caller waits for exit.
  On interruption, destroy the child, force termination if necessary, reap it,
  close streams, and join readers. Preserve the primary interruption and suppress
  cleanup failures. On success, preserve exact output and exit semantics.
  Do not catch interruption as a successful `Exit`.
- [ ] **Step 4: Verify ownership and unchanged Nmap behavior.**
  Run `./gradlew jvmTest --tests '*CommandLineTest' --tests '*NmapNetworkScannerTest'`.
  Expected: cancellation, successful output, stderr, and existing scanner
  classifications pass. Do not change Nmap retries.
- [ ] **Step 5: Review and commit this subprocess fix independently.**
  Suggested message: `fix(scanner): reap interrupted command processes`.

### Task 3: Replace slice threads with structured coroutines

**Files:** Modify
[SlicedApplication.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplication.kt),
[SlicedApplicationTest.kt](../../../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplicationTest.kt),
and the threading section of [scanner.md](../../scanner.md).
Keep resource-order tests in the
[app test package](../../../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/app).

**Interfaces:** Consumes Task 2's interruptible `CommandLine.exec()` and existing
`SliceWorker : AutoCloseable { fun process() }`.
Produces the same `SlicedApplication<T>(slice, updateInterval, open)`,
`start(): SlicedApplicationState.Started<T>`,
`terminate(): SlicedApplicationState.Terminated<T>`, and state-handle methods.
No suspend API is exposed to callers.

- [ ] **Step 1: Characterize lifecycle invariants using barriers.**
  Retain factory/process/close thread equality, failure sets, failure suppression,
  removed-slice closure before reappearance, and reverse resource closure.
  Replace timing ratios with latches and explicit event order.
  Add `concurrent_start_opens_each_slice_once`,
  `terminate_and_wait_share_final_snapshot`, and
  `slice_supplier_failure_closes_active_workers`.
  Assert exactly one open and close per lifetime, identical final sets for all
  waiters, and termination of all workers when the slice supplier fails.
- [ ] **Step 2: Establish the baseline.**
  Run `./gradlew jvmTest --tests '*SlicedApplicationTest'`.
  Existing characterization must pass. Record genuine new regression failures;
  do not force a red test by changing preserved behavior.
- [ ] **Step 3: Replace scheduling internals only.**
  Own a root job/scope, a manager using `delay(updateInterval)`, and one child job
  per active slice. Use `runInterruptible(Dispatchers.IO)` around the entire
  worker lifetime. Record worker failures before cancelling the application.
  Keep cleanup in `finally`; cancellation alone does not enter `failed`.
  Cancel and join removed workers before opening replacements.
  Keep typed states; serialize start/terminate without side-effecting atomic
  update functions or holding a state lock while joining.
- [ ] **Step 4: Add cancellation and shutdown assertions.**
  Add `cancelled_factory_releases_partial_resources` through `NetworkSession.open`
  and `termination_waits_for_noninterruptible_work`.
  Assert cleanup completes once and uninterruptible work is not abandoned.
  Retain processing-primary/close-suppressed failure assertions.
  Exercise an actual blocked `CommandLine.exec()` through a `SliceWorker`;
  assert termination finishes within 5 seconds and the child is gone.
  Verify the existing application owner closes the publisher last.
- [ ] **Step 5: Run the focused owner tests together.**

  ```shell
  ./gradlew jvmTest \
    --tests '*SlicedApplicationTest' \
    --tests '*NetworkSessionTest' \
    --tests '*NetworkResourcesTest' \
    --tests '*ApplicationResourcesTest' \
    --tests '*NetmonScannerTest' \
    --tests '*CommandLineTest'
  ```

  Expected: all selected tests pass, including real blocking work and shutdown.
  Then run `make test-jvm` and `make gradle` sequentially.
  Expected: JVM tests and native arm64 compilation pass.
- [ ] **Step 6: Update threading documentation, inspect, review, and commit.**
  Describe coroutine scheduling without changing the owned-worker contract.
  Suggested message: `refactor(scanner): schedule network slices with coroutines`.

### Task 4: Delegate browser IP parsing to ipaddr.js

**Files:** Modify
[build.gradle.kts](../../../build.gradle.kts),
[IP.js.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/contract/IP.js.kt),
[IPTest.kt](../../../src/commonTest/kotlin/com/bkahlert/netmon/contract/IPTest.kt),
and generated [yarn.lock](../../../kotlin-js-store/yarn.lock).
**Create:** `src/jsTest/kotlin/com/bkahlert/netmon/contract/IPJsTest.kt`
for browser-specific input-acceptance tests.

**Interfaces:** Consumes npm `ipaddr.js` 2.5.0.
Produces the unchanged `IP.of(String): IP`, `IP.of(ByteArray): IP`, `IPv4`,
`IPv6`, `IPSerializer`, and `Cidr` behavior.
Private typed bindings expose `process(String)`, `fromByteArray(Array<Int>)`,
`toByteArray(): Array<Int>`, and expanded IPv6 formatting.

- [ ] **Step 1: Add codec vectors to shared tests.**
  Assert `::` and `::1` produce 16 bytes; `::1` ends with byte 1.
  Assert `fe80::1` stays IPv6 and
  `::ffff:192.168.0.1` equals `192.168.0.1` with four bytes.
  Assert mapped CIDR `/128` becomes IPv4 `/32`, as the shared domain requires.
  Add byte/string/JSON round trips and exact filename strings.
  Pin current compressor output for equal zero runs and a single zero hextet.
  Add shared regression assertions:

  ```kotlin
  IP.of("::").bytes.size shouldBe 16
  IP.of("::1").bytes.last() shouldBe 1.toByte()
  IP.of("::ffff:192.168.0.1") shouldBe IP.of("192.168.0.1")
  ```

  In `IPJsTest`, characterize decimal leading-zero IPv4 and supported surrounding
  characters. Reject repeated `::`, excessive hextets, octets above 255,
  hostnames, integer IPv4, and hex/octal interpretation.
- [ ] **Step 2: Run baseline browser and JVM tests sequentially.**
  Run `make test-js`, then `./gradlew jvmTest --tests '*IPTest'`.
  Expected: zero-prefix and mixed mapped browser regressions expose the old parser.
  Separate those failures from preserved JVM behavior.
  Browser-only restrictions stay in `IPJsTest`; do not change JVM DNS resolution.
- [ ] **Step 3: Pin the dependency and implement the typed JS adapter.**
  Add `implementation(npm("ipaddr.js", "2.5.0"))` to JS dependencies.
  Delegate parsing and byte conversion after the narrow compatibility sanitation.
  Normalize mapped IPv6 via `process()`.
  Preserve decimal semantics before calling the library's permissive IPv4 parser.
  Preserve shared compressor formatting instead of library `toString()`.
  Remove the replaced parser, not the shared compressor or domain types.
- [ ] **Step 4: Regenerate the lockfile and verify both runtimes.**
  Run `make test-js`, then `./gradlew jvmTest --tests '*IPTest'`, then
  `./gradlew jsBrowserDistribution`, sequentially.
  Expected: vectors pass, lockfile records 2.5.0, browser bundle imports the
  CommonJS module correctly, and existing serialized strings remain unchanged.
- [ ] **Step 5: Inspect, review, and commit this codec delivery.**
  Suggested message: `refactor(contract): delegate browser IP parsing to ipaddr.js`.

### Task 5: Prove jUPnP discovery-only suitability

**Files:** Temporarily modify [build.gradle.kts](../../../build.gradle.kts).
Temporarily wire the candidate in
[Application.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt)
only for the isolated native prototype run.
**Create:** `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpDiscovery.kt`
and `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpDiscoveryTest.kt`.
Keep the existing backend wired during this gate.
Record measurements in the SSDP section of the
[design](../specs/2026-10-09-library-simplification-design.md#prototype-gates).

**Interfaces:** Consumes `SsdpLookup` and `DeviceDescription` from
[SsdpCache.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpCache.kt),
`NetworkInterface`, and the existing bounded HTTP helpers.
Produces candidate
`SsdpDiscovery(networkInterface: NetworkInterface, httpClient: HttpClient)`,
implementing `SsdpLookup` and `AutoCloseable`.
Its `device(ip: IP): DeviceDescription?` performs no network I/O.

- [ ] **Step 1: Record the existing backend baseline.**
  Measure source lines, jar/native bytes, startup, idle and scan-peak RSS,
  service memory, and threads using the same fixture network and runtime.
  Keep workload and measurement duration fixed for the candidate.
- [ ] **Step 2: Add 3.0.5 and prove release extension points.**
  Select per-instance interfaces with the release's three-argument
  `NetworkAddressFactoryImpl` constructor.
  Prove a pre-retrieval sender/LOCATION gate for alive and search responses.
  Prove root-only registration without service descriptor hydration, subscriptions,
  or a local HTTP server. Empty `getExclusiveServiceTypes()` means all services,
  not no services. Do not use it as a disabling flag.
  Use bounded discovery executors; record their sizes and rejection behavior.
  Any required protocol fork fails this gate.
- [ ] **Step 3: Add adversarial boundary fixtures.**
  Assert zero HTTP requests for hostname, HTTPS, cross-host, and LAN-to-loopback
  LOCATION values; mapped literal equality remains accepted.
  Assert 262145-byte bodies and stalled headers/body fail within bounds.
  Assert redirects and external entities cause no secondary requests.
  Assert service, icon, embedded-device, and URLBase destinations are never fetched.
  Reuse fixture techniques from
  [DescriptionFetcherTest.kt](../../../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/DescriptionFetcherTest.kt).
- [ ] **Step 4: Add registry and isolation fixtures.**
  Advertise two UDNs at one IP; goodbye one and assert the other remains.
  Set `max-age=1`; without renewal assert removal within 5 seconds, including
  registry maintenance. Do not use a sleep ratio.
  Renew before expiry and assert continued availability.
  Discover the same descriptor URL concurrently in two instances; both must
  populate despite the release's static retrieval deduplication.
  Admit 512 roots; reject root 513, but still accept renewal of an admitted root.
  Assert deterministic smallest-UDN selection and root-only field mapping.
  Close during pending HTTP; assert no sockets, callbacks, or owned tasks remain.
- [ ] **Step 5: Verify JVM and native behavior, not compilation alone.**
  Temporarily wire the candidate into the prototype application before the
  native build so native-image reachability includes the adapter.
  Run `./gradlew jvmTest --tests '*SsdpDiscoveryTest'`, then `make gradle`.
  Exercise the adapter in the produced arm64 native executable with actual UDP
  announcement, descriptor retrieval, expiry, and shutdown fixtures.
  Restore legacy wiring after the prototype measurement, before Task 6.
  Record required native-image metadata and CDDL notices.
  No JVM fallback or increased memory limits are permitted.
- [ ] **Step 6: Make the adoption decision.**
  Compare candidate footprint and maintained production lines with the baseline.
  Pass only if every spec gate holds, SSDP lines decrease, and installed workload
  stays within existing caps. Record measurements, not an assumed thread budget.
  If rejected, remove only this task's prototype/dependency changes and retain
  the backend. Commit the documented decision; do not silently ship fallback.

### Task 6: Adopt the proven SSDP adapter

**Prerequisite:** Task 5 passes. Otherwise, this task is not executed.

**Files:** Modify
[Application.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt),
the [SSDP main package](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp),
the [SSDP test package](../../../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp),
[scanner.md](../../scanner.md), and applicable
[open-issues.md](../../open-issues.md) entries.

**Interfaces:** Consumes Task 5's `SsdpDiscovery` and the existing
`NetworkResources.own(resource)` owner.
Produces the same `SsdpLookup` for `SsdpClues`; no identity changes.
Move `SsdpLookup` and the six-field `DeviceDescription` to a small model file
if needed to delete the old cache file. Do not rename their fields or package.

- [ ] **Step 1: Replace per-session construction.**
  Own `SsdpDiscovery` through session resources instead of `SsdpCache.listen`.
  Keep shared HTTP resources for router discovery.
  Preserve startup search, 10-minute search cadence, and partial-startup cleanup.
- [ ] **Step 2: Remove superseded protocol code and legacy-policy tests.**
  Delete the old cache/listener, datagram parser, and descriptor parsing/fetching
  code only when no remaining production caller uses them.
  Keep shared secure XML and bounded-stream helpers used elsewhere.
  Retain identity tests; replace only fixed-TTL and IP-wide-goodbye expectations.
- [ ] **Step 3: Verify identity and resource integration.**
  Run the discovery, `SsdpCluesTest`, and app-resource tests together, then
  `make test-jvm`. Expected: lookup remains nonblocking, identity policy is
  unchanged, and each session shuts discovery down before shared resources.
- [ ] **Step 4: Verify the installed native service.**
  Run `make build`, `make test-tier1`, `make test-tier2`, and `make soak`
  sequentially on the existing isolated environment.
  Expected: package/boot checks pass, scanner publishes while discovery works,
  no restarts or leaks occur, and service memory stays below 128M.
  Obtain approval before using a shared physical board.
- [ ] **Step 5: Update discovery documentation, inspect, review, and commit.**
  Document max-age expiry, per-root goodbye, deterministic same-IP selection,
  bounded discovery, and native footprint measurements.
  Close issue entries only where the corresponding failure was verified resolved.
  Suggested message: `refactor(scanner): delegate SSDP discovery to jUPnP`.

## Completion gate

For each delivery, inspect every changed file in IntelliJ with warnings included.
Fix findings or explicitly explain retained findings.
Review the scoped diff for API changes, unrelated work, and generated-file drift.
Commit only approved, verified implementation files on the execution branch.
The overall work may finish with Task 5 rejecting jUPnP; report that decision
plainly rather than marking item 6 as adopted.
