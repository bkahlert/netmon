# Library Simplification Design

## Goal and scope

Reduce maintained code through four independent changes from the library audit:

- **Item 1:** Remove unused Heroicons catalogs without a replacement dependency.
- **Item 2:** Replace slice-manager thread coordination with existing coroutines 1.11.0.
- **Item 3:** Delegate browser address parsing to pinned `ipaddr.js` 2.5.0.
- **Item 6:** Evaluate `org.jupnp:org.jupnp:3.0.5` for SSDP discovery and registry management.

This design authorizes planning, not implementation. Each deliverable can ship
without the others. Recommended execution order is 1, 3, 2, then 6.
The SSDP prototype may be rejected without blocking the other deliveries.

Do not replace identity policy, CIDR semantics, MQTT, mDNS, router discovery,
Linux metrics, or scan orchestration. Do not add Caffeine or Apache HttpClient.

## Global constraints

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

## 1. Unused icon catalogs

Delete only these files from the
[icons package](../../../src/jsMain/kotlin/com/bkahlert/netmon/display/presentation/icons):

- [HeroIcons.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/display/presentation/icons/HeroIcons.kt)
- [MiniHeroIcons.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/display/presentation/icons/MiniHeroIcons.kt)
- [OutlineHeroIcons.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/display/presentation/icons/OutlineHeroIcons.kt)
- [SolidHeroIcons.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/display/presentation/icons/SolidHeroIcons.kt)

The audit found no production references outside these catalogs.
Their combined size is 2717 lines. Keep SF Symbols, DeviceIcons, and SVG rendering.
Use selected [official SVG assets](https://github.com/tailwindlabs/heroicons)
if a future feature needs Heroicons; do not import another complete catalog.
Deletion is a behavior-preserving refactor, not a reason to invent a failing test.

## 2. Coroutine-owned slice scheduling

Keep the synchronous facade in
[SlicedApplication.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplication.kt):
the constructor, `start()`, `terminate()`, started-state `waitForTermination()`,
and terminated-state `slices` and `failed` sets remain unchanged.
Keep `SliceWorker.process(): Unit` and `AutoCloseable.close()`.

Replace the manager thread and worker-thread bookkeeping with one owned root
job, a manager coroutine using `delay`, and child jobs per slice.
Do not use supervisor semantics: a worker failure terminates the application.
Keep typed lifecycle handles and serialize transitions without holding locks
across joins. Concurrent `start()` calls must not create duplicate workers.

Run each worker's entire open/process/close lifetime inside `runInterruptible`
on `Dispatchers.IO`. This retains the existing same-thread factory/process/close
contract. Cancellation is not a slice failure. Factory, processing, and cleanup
failures remain logged and recorded; cleanup failure is suppressed onto an
existing processing failure. Cleanup must execute exactly once after cancellation.
Reconciliation must join and close a removed worker before reopening that slice.

[NetworkSession](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/NetworkSession.kt)
continues to own partial-startup cleanup.
[ApplicationResources](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/ApplicationResources.kt)
continues to close the manager before the publisher.
This supersedes only the no-coroutines scheduling exclusion in the
[worker lifecycle design](2026-10-08-scanner-worker-lifecycle-design.md).
Its ownership and failure guarantees still apply.

### Subprocess cancellation is part of the delivery

[CommandLine.exec()](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/support/exec/CommandLine.kt)
currently reads stdout before interruptible `waitFor()`.
Interrupting a coroutine does not prove that pipe reads stop or children exit.
Move pipe draining off the waiting caller. On interruption, destroy and reap
the owned child, close streams, and join both drainers before propagating the
interruption. Preserve successful output, stderr, exit codes, and Nmap retry policy.
Do not leave detached children, daemon-reader leaks, or a cancellation watcher.

Use synchronization barriers in lifecycle tests, not sleep ratios.
Retain coverage for uninterruptible work: termination waits until it completes.
Do not introduce dispatcher parameters solely to let tests bypass production behavior.

## 3. Browser address codec

Keep the actual types in
[IP.js.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/contract/IP.js.kt)
and their public construction, comparison, and serialization surfaces.
Add minimal typed `@JsModule("ipaddr.js")` bindings beside the JS implementation.
Delegate syntax and byte conversion to `process()` and `fromByteArray()`.
Convert JS unsigned octets to Kotlin bytes and back explicitly.
Do not use broad `dynamic` or `Any` casts.

Retain supported surrounding-input sanitation and decimal four-part IPv4.
Do not accidentally admit hostnames, integer IPv4, octal, or hexadecimal IPv4.
Characterize leading-zero decimal input before replacing the parser.
Normalize IPv4-mapped IPv6 to IPv4 consistently with existing shared tests.
Correct zero-prefix IPv6 and mixed dotted mapped literals as part of this codec.

Keep the shared `IPv6Compressor`, including its existing single-zero and tie
behavior. It is also used by the JVM implementation.
Use the library's expanded IPv6 representation before this compressor; do not
substitute RFC 5952 `toString()` for existing wire formatting.
If using `toNormalizedString()`, confine its deprecated API to the adapter.
Do not change signed-byte comparison or JVM hostname resolution in this work.

Declare npm 2.5.0 in
[build.gradle.kts](../../../build.gradle.kts) and regenerate
[Yarn lockfile](../../../kotlin-js-store/yarn.lock) through Gradle.
The library is MIT licensed.
[Official API documentation](https://github.com/whitequark/ipaddr.js)
and the pinned source determine the binding, not a different IP package.

## 6. SSDP discovery through jUPnP

The user approved advertised `max-age` expiry and device-specific goodbye
messages. Do not recreate the legacy fixed 30-minute TTL, negative-result TTL,
or IP-wide goodbye policy. Accept jUPnP's standard retry and registry behavior.
Retain a bounded registry with at most 512 root devices per session.
Reject new roots when full; updates to admitted roots remain allowed.

Keep `SsdpLookup.device(ip: IP): DeviceDescription?` nonblocking.
No jUPnP types cross into
[SsdpClues](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/identity/SsdpClues.kt).
Map only root-device friendly name, manufacturer, model name, model number,
device type, and UDN. Trim blank strings to null, as today.
For multiple roots at one IP, choose the lexically smallest UDN.
A goodbye for one root must leave another root at that IP available.

The candidate adapter is `SsdpDiscovery(networkInterface, httpClient)`,
implementing `SsdpLookup` and `AutoCloseable` in the
[SSDP package](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp).
The per-session resource owner constructs and closes it.
Construction failure must close partial resources.
Known unavailable multicast interfaces retain a warning and disabled lookup;
other initialization failures must not silently become success.
Search on startup and every 10 minutes, preserving current discovery cadence.

### Prototype gates

Version 3.0.5 supports per-instance interface selection through
`NetworkAddressFactoryImpl(int, int, String)`.
The [release configuration](https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/DefaultUpnpServiceConfiguration.java)
uses a 16-core/200-maximum executor and queue capacity 1000.
These defaults are not acceptable without footprint measurements and an
explicit bounded discovery-only configuration.

The [descriptor retrieval implementation][descriptor-retrieval]
fetches service descriptors and shares a static active-retrieval map.
Prove independent sessions discovering the same URL do not lose discovery.
Do not fetch services, icons, URLBase-derived destinations, or subscriptions.
Do not expose a local HTTP server just to read remote device identity.

Before adoption, demonstrate all of these using the pinned release:

- Validate sender/LOCATION equality before both registry updates and retrieval.
- Restrict LOCATION to literal-IP HTTP, including mapped-address equality.
- Reuse bounded HTTP handling without redirects or secondary descriptor requests.
- Prevent external XML entities and preserve root-only identity extraction.
- Bound workers, queued retrievals, and roots without copying a second TTL cache.
- Close sockets, outstanding HTTP operations, executor tasks, and registry callbacks.
- Build and run the arm64 native executable without JVM fallback.
- Complete installed boot and soak checks under unchanged memory limits.
- Show net maintained SSDP production lines decrease against the current backend.

Prefer release extension points over a fork. jUPnP is CDDL-1.0 licensed;
review redistribution notices before packaging.
If safe discovery-only use requires a protocol rewrite or fails any gate,
stop adoption and keep the current backend. Do not increase memory limits,
ship both backends, or silently fall back at runtime.

### Task 5 decision: reject 3.0.5 before runtime prototyping

The 2026-10-09 source feasibility gate is **REJECT**. Keep `SsdpCache` wired.
No candidate, dependency, or application-wiring changes were introduced.
This rejects the pinned release under the isolation and no-protocol-rewrite
constraints; it is not a native-runtime or footprint result.

The [published 3.0.5 sources][task5-sources] and binary were inspected.
The retrieval source matches the release tag byte-for-byte.
The decisive path is:

- [RetrieveRemoteDescriptors.java][descriptor-retrieval], lines 71–73 and
  101–112, uses a private static map keyed only by descriptor URL.
  A second session's retrieval returns without describing or registering its
  root while the first session retrieves that URL.
- [ReceivingSearchResponse.java][task5-search-response], lines 97–110,
  also drops that response **before** consulting the configured retrieval
  executor. No retry or cross-session delivery is scheduled on that branch.
- [ReceivingNotification.java][task5-notification], lines 118–126, and the
  search-response path instantiate `RetrieveRemoteDescriptors` directly.
  [ProtocolFactoryImpl.java][task5-protocol-factory], lines 105–115, provides
  receiving-protocol factories, but no remote-descriptor-retriever factory.
  Binary signatures confirm the private map/device and absent factory.

Supported alternatives were checked before rejecting:

- A protocol-factory guard can enforce literal HTTP sender/LOCATION equality
  before delegating either receiving path, including before registry renewal.
- A secure root-only `DeviceDescriptorBinder` can omit services and embedded
  devices before hydration. This avoids secondary requests without pretending
  an empty exclusive-service array disables them.
- The per-instance address constructor, nullable stream-server factory,
  executor configuration, and registry subclass provide useful boundaries.
  None replaces the release's cross-session retrieval deduplication.
- Per-session single-thread executors cannot serialize other sessions.
  `ReceivingAsync.waitBeforeExecution()` provides no atomic reservation;
  polling the public static probe still races with another session's retrieval.
  Binder, HTTP-client, and registry-listener hooks are downstream of the drop.
- Process-wide serialization could prevent overlapping retrievals, but would
  couple sessions' discovery scheduling instead of keeping discovery isolated.
  Replacing receiver execution or swapping retrieval tasks in an executor
  would maintain new protocol orchestration. Neither is an accepted workaround.

The existing SSDP package contains **299 physical Kotlin lines**, including
comments and blank lines:
[DeviceDescription.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/DeviceDescription.kt) 62,
[SsdpCache.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpCache.kt) 206, and
[SsdpMessage.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpMessage.kt) 31.
This is a source count, not a footprint measurement.
Candidate source reduction is **NOT RUN**.
Adversarial and registry/isolation fixtures, candidate JVM tests, native build
and native UDP/HTTP/expiry/shutdown execution are **NOT RUN**.
Same-runtime baseline/candidate jar and native bytes, startup, idle and scan-peak
RSS, service memory and threads are **NOT RUN**.
Installed boot/soak checks under `64m`/`128M` limits are **NOT RUN**.
No historical measurement substitutes for these gates.
Task 3's arm64 compilation does not establish Task 5 runtime or footprint.

The release's CDDL-1.0 manifest and license were reviewed.
Any future redistribution needs covered-source availability, a license copy,
and retained attribution; rejection adds no packaged library or copied source.
The full [Task 5 report][task5-report] records alternatives, commands, hashes,
limitations, and the unchanged production boundary.
Pinned artifacts and command logs remain in the ignored
[evidence workspace][task5-evidence].

## Evidence and acceptance

Record baseline and candidate source lines, artifact bytes, startup time,
idle/scan-peak RSS, service memory, and thread counts on the same runtime.
Historical footprint numbers are context, not a baseline.
The existing Makefile supplies browser, JVM, native, packaging, boot, and soak
targets; do not run Gradle builds concurrently.
Shared-board installation requires approval.

Update only the threading and discovery sections of
[scanner.md](../../scanner.md) when implementation lands.
Update matching entries in [open-issues.md](../../open-issues.md) only when
the issue's observable failure is actually resolved.

[descriptor-retrieval]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/RetrieveRemoteDescriptors.java
[task5-sources]: https://repo.maven.apache.org/maven2/org/jupnp/org.jupnp/3.0.5/org.jupnp-3.0.5-sources.jar
[task5-search-response]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/async/ReceivingSearchResponse.java
[task5-notification]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/async/ReceivingNotification.java
[task5-protocol-factory]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/ProtocolFactoryImpl.java
[task5-report]: ../../../.superpowers/sdd/2026-10-09-library-simplification/task-5-report.md
[task5-evidence]: ../../../.cache/task-5-evidence/
