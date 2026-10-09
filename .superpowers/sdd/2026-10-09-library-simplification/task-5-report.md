# Task 5: jUPnP discovery-only feasibility gate

## Decision and scope

**REJECT — source-only study of `org.jupnp:org.jupnp:3.0.5`.**
No adoption, candidate implementation, dependency declaration, or production
wiring change was made.
The existing `SsdpCache` remains the only wired SSDP backend.

Worktree: `/Users/bkahlert/Development/com.bkahlert/netmon/.worktrees/library-simplification`.
Branch: `refactor/library-simplification`.
Starting commit: `cd640bdf0b2e745b0bf1680bda30821d7d569f5c`.
The starting worktree was clean.
The [brief](task-5-brief.md), shared guidance, and binding
[design](../../../docs/superpowers/specs/2026-10-09-library-simplification-design.md)
were read; the whole plan was not read.
The controller's source-feasibility-first ruling was applied.
The ledger was not edited.

QA was declared before any change:

> QA for this change: pinned-source SPI audit and diff checks — verified by a
> traceable decision with unchanged production wiring.

## Authoritative artifacts and evidence

The [published source JAR][maven-sources], [binary JAR][maven-binary], and
[POM][maven-pom] were downloaded into the ignored
[evidence workspace](../../../.cache/task-5-evidence/).
No Gradle dependency was added or resolved.
Local sibling/worktree searches found no existing jUPnP source checkout.
`Context7.resolve-library-id("jUPnP", …)` returned only CyberGarage UPnP.
That unrelated result was not used.
Only public dependency names/questions were sent to Context7.

SHA-256 values:

| Artifact | SHA-256 |
|---|---|
| Source JAR | `e34281af2b2c7c1ffd89d1e34490d7a13c2c422453ec53bdca74a0becf191905` |
| Binary JAR | `a6f76dc1baa3d4a4e14c55bcaa93a1bd1915b2040c1ed52e25bdbd8892daa1eb` |
| POM | `ecfa7f579ab678bf9a32bb99e002c8d070b4096cc12fbf13992a63aa26aed5a4` |

The [release-tag retrieval source][retrieval] was separately downloaded.
`cmp` verified equality with the Maven source-JAR file.
The binary manifest declares `Bundle-Version: 3.0.5` and CDDL-1.0.
`javap -p` confirmed the relevant signatures against the published binary.
The source archive contained 419 entries.
All Java line references below refer to those extracted release sources.

Persisted evidence:

- [source-audit.log](../../../.cache/task-5-evidence/source-audit.log):
  branch/base, hashes, tag comparison, binary signatures, source count,
  unchanged production diff.
- [extension-point-audit.log](../../../.cache/task-5-evidence/extension-point-audit.log):
  numbered source excerpts and six mechanical source assertions.
- [download-sha256.txt](../../../.cache/task-5-evidence/download-sha256.txt):
  initial source-JAR and POM hashes.
- [LICENSE.txt](../../../.cache/task-5-evidence/LICENSE.txt):
  the pinned release license.

## Decisive release behavior

[RetrieveRemoteDescriptors.java][retrieval]:

- Lines 70–74: the current device and static active-retrieval map are private.
  The map key is the descriptor `URL`, not service/session identity.
- Lines 88–110: `run()` checks the owning registry, then performs the global
  `putIfAbsent`. A collision returns immediately.
  Only the winning task calls `describe()`; the map entry is removed afterward.
- Lines 197–226: descriptor binding, discovery-start notification, hydration,
  and registration use that task's `getUpnpService()`.
  There is no delivery to another session on the collision branch.
- Lines 392–396: the public static probe only reads the same map.
  It is not an atomic per-session reservation or a completion callback.

The source establishes a concrete counterexample without running a prototype:

1. Session A starts retrieving URL U and wins the static map entry.
2. Session B receives its first valid response for U while A is retrieving.
3. [ReceivingSearchResponse.java][search-response]:97–100 observes the entry
   and returns before obtaining B's async executor.
4. A registers its root in A's registry and removes the map entry.
5. B never entered binding or registration, and that branch schedules no retry.
   A successful fetch does not populate B's registry.

For alive announcements, B can reach its retrieval executor, but B's concrete
retrieval task returns at the same static-map collision.
This is a source-derived execution trace, **not a runtime test result**.
Future repeated announcements might recover B; that is not independent
discovery of a concurrently announced descriptor URL.

[ReceivingNotification.java][notification]:118–126 calls registry renewal and
then directly constructs `new RetrieveRemoteDescriptors(...)`.
[ReceivingSearchResponse.java][search-response]:71–110 does the same renewal,
the additional static check, and the same concrete allocation.
There are exactly two concrete retrieval allocation sites in the release.
[ProtocolFactory.java][factory-interface] has no descriptor-retriever factory.
[ProtocolFactoryImpl.java][factory]:105–115 only injects receiving protocols.

## Supported SPI paths and alternatives investigated

These alternatives were considered before rejection.
Supported extension points are not themselves failures.
Their runtime behavior remains untested here.

| Requirement or alternative | Exact release evidence | Conclusion |
|---|---|---|
| Per-instance network interface | [NetworkAddressFactoryImpl.java][address]:79–109; [DefaultUpnpServiceConfiguration.java][configuration]:303–315 | The three-argument constructor accepts an instance interface string. A nonblank interface avoids the interface system-property fallback. The constructor also reads an address property; no global property would be set. This is not the blocker. |
| Validate sender before registry/retrieval | [ProtocolFactoryImpl.java][factory]:87–115; [RouterImpl.java][router]:253–265; [ReceivingNotification.java][notification]:118; [ReceivingSearchResponse.java][search-response]:71 | `createReceivingAsync` or its notification/search-response factories can guard the raw LOCATION and sender, then delegate. The guard must precede both renewal paths, not merely HTTP. Root-target filtering also belongs at this boundary. Literal HTTP and mapped-address equality need fixtures; no fixture pass is claimed. |
| Root-only descriptor model | [DeviceDescriptorBinder.java][binder]:29–39; [DefaultUpnpServiceConfiguration.java][configuration]:219–220, 329–330; [RetrieveRemoteDescriptors.java][retrieval]:197–205, 254–300 | A configured secure binder can construct a root without services, icons, embedded devices, or URLBase-derived destinations. Then hydration has nothing secondary to fetch. Existing root parsing can be reused. This supported route avoids a service-hydration rewrite, but executes only after winning the static map. |
| Exclusive service types | [UpnpServiceConfiguration.java][configuration-interface]:123–142; [ProtocolFactoryImpl.java][factory]:132–157; [RetrieveRemoteDescriptors.java][retrieval]:356–377 | Empty means all. Null disables discovery in the default factory, not just services. A nonmatching sentinel type is not a reliable adversarial policy, and constrains advertisements. These are not a service-disable switch. |
| Bounded HTTP and XML | [StreamClient.java][stream-client]:20–80; [DefaultUpnpServiceConfiguration.java][configuration]:182–184, 329–330; [UDA10DeviceDescriptorBinderImpl.java][uda-binder]:75–110 | The client and binder are configurable. Existing bounded HTTP/root XML boundaries could be reused. The default DOM parser path does not set entity-disabling features there; a secure binder is required. Client/binder changes cannot revive a retrieval dropped upstream. |
| No local HTTP server | [RouterImpl.java][router]:392–404 | `createStreamServer` returning null is explicitly handled. No fake local listener is needed. This is not a blocker. |
| Bounded executors | [DefaultUpnpServiceConfiguration.java][configuration]:99–102, 273–299, 345–368; [UpnpServiceConfiguration.java][configuration-interface]:228–257 | Default core 16, maximum 200, queue 1000, discard-with-warning rejection. Executor factories/getters allow bounded alternatives. Those are source constants, not measured live thread counts. No candidate pool sizes or rejection behavior were implemented or tested. |
| Registry capacity/expiry | [UpnpServiceImpl.java][service]:136–142; [RegistryImpl.java][registry]:210–236; [RemoteItems.java][remote-items]:86–98, 128–159, 250–267 | A registry subclass can reject new roots while allowing renewal, using registry locks rather than another TTL cache. The release owns advertised-age expiry. None of this repairs the dropped initial retrieval. Capacity, timing, root mapping and goodbye behavior remain NOT RUN. |
| Retrieval subclass | [RetrieveRemoteDescriptors.java][retrieval]:86–113, 115, 184, 254; [ReceivingNotification.java][notification]:126; [ReceivingSearchResponse.java][search-response]:110 | `run` and description methods are overridable, but default receivers allocate the concrete release class. There is no supported retriever supplier to install such a subclass while retaining receiver execution. Merely defining a subclass changes nothing. |
| Single-thread executor per session | [RouterImpl.java][router]:265; [ReceivingSearchResponse.java][search-response]:97–110; [RetrieveRemoteDescriptors.java][retrieval]:99–101 | Bounds/serialization inside B do not serialize A. Search responses are dropped before B's retrieval executor is called; alive tasks can collide inside `run`. Per-instance serialization is insufficient. |
| Pause before receiver execution | [ReceivingAsync.java][receiving-async]:60–95; [RetrieveRemoteDescriptors.java][retrieval]:392–396 | `waitBeforeExecution` can pause, but probing then delegating is not atomic with another session's `putIfAbsent`. A finite delay or poll cannot guarantee absence of the race. Adding own retries or pending-delivery machinery replaces release orchestration. |
| Registry listener as recovery hook | [RegistryImpl.java][registry]:134–145; [RetrieveRemoteDescriptors.java][retrieval]:101–112, 197–202 | Discovery-start listeners run after the winning retrieval binds the descriptor. The losing session receives no such callback. Cross-session descriptor forwarding would add a shared discovery mechanism rather than isolate sessions. |
| Global serialization/shared executor | [RouterImpl.java][router]:253–265; [RetrieveRemoteDescriptors.java][retrieval]:99–110 | Serializing all sessions' receiving and retrieval work process-wide could avoid overlaps. This was considered, not overlooked. It couples discovery scheduling and ownership across network sessions; it is excluded by per-session discovery isolation. Serializing only retrieval tasks also misses the earlier search-response drop. |
| Executor task substitution/thread-local context | [UpnpServiceConfiguration.java][configuration-interface]:228–231; [RetrieveRemoteDescriptors.java][retrieval]:71, 86–113 | Replacing the concrete runnable, reconstructing its private device from incoming context, and bypassing its `run` implements a new retrieval dispatcher. Scheduling is configurable; no protocol replacement hook is supplied by the executor contract. This is protocol orchestration maintained by us, not a normal bounded-executor configuration. |
| Replace receiver execution | [ProtocolFactoryImpl.java][factory]:105–115; [ReceivingNotification.java][notification]:81–141; [ReceivingSearchResponse.java][search-response]:56–113 | Factories allow new receiving implementations. Reimplementing validation, renew/remove, retrieval scheduling and callbacks could avoid static deduplication, but is the prohibited protocol rewrite, not delegation to the release behavior. |
| Change URL identity/reflection/fork | [RemoteDeviceIdentity.java][identity]:45–74; [RetrieveRemoteDescriptors.java][retrieval]:74, 90–110 | Adding synthetic URL identity, changing URL equality, modifying private state reflectively, or patching upstream internals is not a supported release configuration. It would evade the requirement, not prove independent release behavior. No such workaround was implemented. |

The rejection is deliberately narrow.
It does not claim jUPnP lacks interface, security, binder, server, or executor
extension points.
It rejects independent concurrent discovery with unchanged release protocol
orchestration under this project's isolation/no-rewrite constraints.
Process-global serialization was excluded on those constraints, not by a
measured performance claim.

## Actual commands and output

Commands ran from the specified worktree unless stated otherwise.
Public downloads used `curl --fail --silent --show-error --location`.
The source JAR was extracted using Python's `zipfile`, into the ignored
workspace, not a temporary directory.

The persisted [source audit](../../../.cache/task-5-evidence/source-audit.log)
contains this actual output:

```text
$ git branch --show-current; git rev-parse HEAD; git status --short
refactor/library-simplification
cd640bdf0b2e745b0bf1680bda30821d7d569f5c
$ cmp tag-RetrieveRemoteDescriptors.java sources/org/jupnp/protocol/RetrieveRemoteDescriptors.java
PASS: Maven sources match release tag for retrieval
```

The relevant binary signatures from `javap -p`:

```text
private org.jupnp.model.meta.RemoteDevice rd;
private static final java.util.concurrent.ConcurrentHashMap<java.net.URL, java.lang.Boolean> activeRetrievals;
public void run();
protected void describe() throws org.jupnp.transport.RouterException;
public static boolean isRetrievalInProgress(org.jupnp.model.meta.RemoteDevice);
```

The interface dump lists receiving/sending factories, but no remote retrieval
factory. Full output is in the same log.

The source assertion command checked the private/static declarations,
the search-response static check preceding executor submission,
renewal preceding notification retrieval, absence of a factory hook,
and exactly two concrete allocation sites.
Its output:

```text
Concrete release retrieval allocation sites:
protocol/async/ReceivingNotification.java:126: .execute(new RetrieveRemoteDescriptors(getUpnpService(), rd));
protocol/async/ReceivingSearchResponse.java:110: executor.execute(new RetrieveRemoteDescriptors(getUpnpService(), rd));

PASS: 6 source assertions (static URL map; private device; pre-executor response check; update-before-notification-retrieval; absent factory SPI; exactly two concrete allocation sites).
These are source assertions, NOT runtime/fixture tests.
```

Physical line counting used
`wc -l src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/*.kt`.
Actual output:

```text
      62 src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/DeviceDescription.kt
     206 src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpCache.kt
      31 src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpMessage.kt
     299 total
```

This includes comments, blanks, `SsdpLookup`, and bounded `DescriptionFetcher`
within the [cache file](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery/ssdp/SsdpCache.kt).
It excludes shared HTTP/XML helpers and application wiring.
There is no candidate count or claim of a line decrease.

`git diff cd640bdf0b2e745b0bf1680bda30821d7d569f5c -- src build.gradle.kts gradle.properties Makefile`
produced no output.
The production [application](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt):135
still constructs `SsdpCache(DescriptionFetcher(lanHttp))`.
No native build was started and no Gradle invocation was needed.

## Gate disposition and unrun validation

| Gate | Outcome |
|---|---|
| Pinned-release discovery-only SPI feasibility | **REJECT**: independent same-URL sessions cannot retain default retrieval orchestration without shared process-wide coordination or a protocol rewrite. |
| Literal sender/LOCATION, including mapped equality, before renewal/retrieval | Source extension point identified; functional proof **NOT RUN**. |
| Secure root-only model and no service/icon/embedded/URLBase fetches | Source extension points identified; functional proof **NOT RUN**. |
| No HTTP server | Source null-server path identified; runtime socket check **NOT RUN**. |
| Bounded executors/queues and rejection behavior | Configurable source boundary identified; candidate sizing and functional proof **NOT RUN**. |
| Hostname, HTTPS, cross-host, LAN-to-loopback zero-request fixtures | **NOT RUN**. |
| 262145-byte body, stalled headers/body, 3-second bounds | **NOT RUN**. |
| Redirect and external-entity secondary-request fixtures | **NOT RUN**. |
| Service/icon/embedded/URLBase adversarial fixtures | **NOT RUN**. |
| Two UDNs/one IP, one-root goodbye, smallest-UDN mapping | **NOT RUN**. |
| `max-age=1` removal within 5 seconds; renewal | **NOT RUN**. |
| Concurrent same-URL instances | Source counterexample established; runtime fixture **NOT RUN**. |
| 512-root cap, root 513 rejection, admitted-root renewal | **NOT RUN**. |
| Close during pending HTTP, no tasks/sockets/callbacks | **NOT RUN**. |
| `./gradlew jvmTest --tests '*SsdpDiscoveryTest'` | **NOT RUN**; no functional candidate was created after source rejection. |
| `make gradle` / arm64 candidate native build | **NOT RUN**. |
| Candidate native UDP announcement, descriptor, expiry, shutdown | **NOT RUN**. |
| Native-image metadata | **NOT RUN**; none generated or claimed sufficient. |
| Net maintained SSDP production-line reduction | **NOT RUN** for candidate; baseline physical count 299 only. |
| Same-runtime footprint comparison | **NOT RUN**. |
| Installed boot/soak, native heap `64m`, service `MemoryMax=128M` | **NOT RUN**; no deployment was requested or attempted. |

The controller permits stopping before expensive measurements when source
proves the protocol/isolated-session blocker.
No unrun gate is marked passed.
No functional test exemption is being applied to a shipped prototype.
There is no functional code change to test.
Task 3's final arm64 compilation is historical context only.
Task 3 runtime footprint remains unmeasured and was not reused as baseline.

Actual measurements:

| Metric | Existing backend | Candidate |
|---|---|---|
| SSDP physical source lines | 299 | NOT RUN / no candidate |
| Same-runtime jar bytes | NOT RUN | NOT RUN |
| Same-runtime native executable bytes | NOT RUN | NOT RUN |
| Startup duration | NOT RUN | NOT RUN |
| Idle RSS | NOT RUN | NOT RUN |
| Scan-peak RSS | NOT RUN | NOT RUN |
| Service memory | NOT RUN | NOT RUN |
| Live thread counts | NOT RUN | NOT RUN |

No workload/duration comparison was performed.
Default executor constants are not a runtime measurement.

## Licensing

The [release LICENSE.txt][license] declares CDDL version 1 or later.
Java file headers carry `SPDX-License-Identifier: CDDL-1.0`;
the binary manifest declares CDDL-1.0.
The binary archive lists no standalone LICENSE/NOTICE file, so packaging must
not assume the manifest alone supplies a license copy.

License sections 3.1, 3.3, and 3.5 require covered-source availability,
source licensing, retained notices, and compliance when distributing binaries.
Section 3.6 permits a larger work while retaining covered-software obligations.
A future native package would need an explicit CDDL notice/license and
covered-source access, not merely a Gradle coordinate.
No covered source was copied into maintained code or packaged.
Downloaded public artifacts remain ignored evidence; no application
redistribution notices changed.
This is a technical notice review, not legal advice.

## Changed files, restoration, limitations

The only tracked change is the SSDP decision in the
[design spec](../../../docs/superpowers/specs/2026-10-09-library-simplification-design.md).
This report and evidence are ignored persistent workspace artifacts.
No prototype or dependency changes needed removal.
No reset, checkout, fallback, shared-board install, push, merge, or
original-checkout edit was used.

`IP`, `SsdpLookup`, all six identity fields, Kotlin, JDK, coroutines,
memory limits, and production legacy wiring remain unchanged.
The [scanner.md](../../../docs/scanner.md) and
[open-issues.md](../../../docs/open-issues.md) remain unchanged:
no new implementation landed and no legacy failure was resolved.

The decision includes a policy judgment:
process-wide coordination is incompatible with the binding per-session
discovery-isolation requirement.
Without that constraint, global serialization is a possible workaround,
not a proven performance failure.
Changing that requirement or permitting new retrieval protocol orchestration
would require a new controller decision and the full unrun gates.
This task did neither.

## Final validation

`git diff --check` passed.
Only the Task 5 design-spec decision is tracked as changed.
The production/dependency/build-setting diff against the required base is empty.
No candidate or candidate test file exists.
The [documentation validation log](../../../.cache/task-5-evidence/documentation-validation.log)
records the pre-commit results and 19 valid Task 5 local links.

The first whole-spec link audit stopped on the pre-existing
`HeroIcons.kt` deletion reference in the icon section.
It was not a Task 5 failure; historical links were left unchanged.
The scoped Task 5 link audit passed.

IDE inspection was requested with `errorsOnly=false`.
Initial calls using the worktree as `projectPath` returned an original-relative
spec result and could not find the report; those calls were not accepted as
actual-worktree validation.
Both files were then inspected using worktree-qualified paths under the open
IDE project.
A report capitalization warning was fixed.
The final responses identify the actual paths:

```text
.worktrees/library-simplification/docs/superpowers/specs/2026-10-09-library-simplification-design.md
errors: []
.worktrees/library-simplification/.superpowers/sdd/2026-10-09-library-simplification/task-5-report.md
errors: []
```

No excluded-worktree Kotlin inspection is claimed; no Kotlin file changed.
No external reviewer or helper agent was used.
Self-review checked the source counterexample, alternate SPI paths, exact
citations, unchanged legacy wiring, explicit unrun gates, and licensing.
The remaining concern is the disclosed isolation-policy judgment about global
coordination; the controller independently reviews the decision.

Committed decision:
`46b71fe113c7ca97d3fc0fea16e49d14625153c2`
— `docs(ssdp): reject jupnp 3.0.5 discovery-only gate`.
The [commit verification log](../../../.cache/task-5-evidence/commit-verification.log)
shows parent `cd640bdf0b2e745b0bf1680bda30821d7d569f5c`,
one changed tracked file, 72 insertions, an empty tracked status, and an empty
production diff against the required base.

[maven-sources]: https://repo.maven.apache.org/maven2/org/jupnp/org.jupnp/3.0.5/org.jupnp-3.0.5-sources.jar
[maven-binary]: https://repo.maven.apache.org/maven2/org/jupnp/org.jupnp/3.0.5/org.jupnp-3.0.5.jar
[maven-pom]: https://repo.maven.apache.org/maven2/org/jupnp/org.jupnp/3.0.5/org.jupnp-3.0.5.pom
[retrieval]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/RetrieveRemoteDescriptors.java
[search-response]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/async/ReceivingSearchResponse.java
[notification]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/async/ReceivingNotification.java
[factory-interface]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/ProtocolFactory.java
[factory]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/ProtocolFactoryImpl.java
[address]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/transport/impl/NetworkAddressFactoryImpl.java
[configuration]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/DefaultUpnpServiceConfiguration.java
[configuration-interface]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/UpnpServiceConfiguration.java
[router]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/transport/RouterImpl.java
[binder]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/binding/xml/DeviceDescriptorBinder.java
[stream-client]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/transport/spi/StreamClient.java
[uda-binder]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/binding/xml/UDA10DeviceDescriptorBinderImpl.java
[service]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/UpnpServiceImpl.java
[registry]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/registry/RegistryImpl.java
[remote-items]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/registry/RemoteItems.java
[receiving-async]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/protocol/ReceivingAsync.java
[identity]: https://github.com/jupnp/jupnp/blob/3.0.5/bundles/org.jupnp/src/main/java/org/jupnp/model/meta/RemoteDeviceIdentity.java
[license]: https://github.com/jupnp/jupnp/blob/3.0.5/LICENSE.txt

## FIXROUND1

### Status and decision

Decision: **REJECT** (by measured source-line gate). `SsdpCache` remains wired.
The review finding was correct: the original rejection relied on excluding
process-wide serialization. Under the controller ruling (shared scheduling is not
automatically an isolation violation), the alternative is **source-feasible**.

### Corrected claims

- WRONG (original): "the search-response drop defeats every workaround" and
  "process-wide serialization couples sessions instead of keeping discovery isolated".
- Evidence (pinned 3.0.5 sources in `.cache/task-5-evidence/sources`):
  - `RouterImpl.received` (L253–265) executes every `ReceivingAsync` on
    `getRemoteListenerExecutor()`.
  - `RetrieveRemoteDescriptors` is only allocated at `ReceivingNotification` L126 and
    `ReceivingSearchResponse` L110; `activeRetrievals` put/remove happen only inside
    its `run()` (L101–112).
  - Per-session single-thread remote-listener executor whose tasks hold one
    process-wide fair `ReentrantLock`, plus `getAsyncProtocolExecutor()` running
    inline on that thread, means no receipt overlaps any retrieval in any session:
    the map is empty whenever `isRetrievalInProgress` (L97) or `run()` checks it.
    Neither drop branch can fire.

### Prototype (archived, removed from tree)

Archived to `.cache/task-5-evidence/prototype/`: `SsdpDiscovery.kt` (212 lines),
`SsdpDiscoveryTest.kt` (414 lines), `build.gradle.kts.diff`
(`implementation("org.jupnp:org.jupnp:3.0.5")`). Design: `DefaultUpnpServiceConfiguration`
subclass (bounded default pool 0..8 SynchronousQueue/Abort; serial per-session executor
queue 64 DiscardPolicy under process-wide fair lock for remote-listener + async protocol;
`NetworkAddressFactoryImpl(..., ifname)`; null stream server; `HttpClient` stream client,
256 KiB bounded body, 3 s, literal http only); root-only binder reusing
`DeviceDescription`; gate `ProtocolFactoryImpl` (NT/ST `upnp:rootdevice` only, literal
HTTP LOCATION host == sender, byebye only from registered root's sender); registry cap 512.

### Commands and actual output

- `./gradlew compileKotlinJvm` with prototype: success.
- `.cache/task-5-evidence/run-isolated-jvm-test.sh` (podman `eclipse-temurin:17-jdk`,
  isolated netns, host-built classpath): `12 tests successful, 0 tests failed`
  (`isolated-jvm-test-run2.log`). Rerun requires restoring the archived prototype files.
- Mutation (per-session lock instead of process-wide):
  `10 tests successful, 2 tests failed` — `registers_a_url_retrieved_concurrently_by_two_sessions_in_both`,
  `keeps_another_session_s_roots_on_close` (`isolated-mutation-per-session-lock.log`).
- JVM tests under native-image agent: 12/12 pass (`agent-jvm-test.log`); agent emitted
  9 jUPnP reflection entries (EXT, HOST, LOCATION, MaxAge, NTS, RootDevice, STAll,
  Server, USNRootDevice headers) in `agent-config/reachability-metadata.json`.
- An earlier run on the real mac LAN (en0) is invalid: TTL-0 multicast reached LAN
  devices, real roots filled the cap; 7/12 passed. Not repeated.

### Line gate (decisive)

| Item | Lines |
| --- | --- |
| Replaced baseline: `SsdpCache.kt` 206 + `SsdpMessage.kt` 31 | 237 |
| Reused by both: `DeviceDescription.kt` | 62 |
| Candidate `SsdpDiscovery.kt` + moved `SsdpLookup` | 212 + 5 = 217 |
| Native reflection metadata (one line/entry, project format) | 21 |
| **Candidate total** | **238 (+1)** |

`UpnpHeaders.parseHeaders` (L56–70) parses every known UPnP header; `UpnpHeader.newInstance`
(L146–165) instantiates each `Type` class reflectively, logging ERROR on failure.
Reachable classes for SSDP traffic: NT (RootDevice, UDADeviceType, UDAServiceType,
DeviceType, ServiceType, UDN, NTEvent), USN (USNRootDevice, DeviceUSN, ServiceUSN),
ST (STAll), NTS, HOST, SERVER, LOCATION, CACHE-CONTROL, USER-AGENT, CONTENT-TYPE, MAN,
MX, EXT = 21. Fixture-only 9 gives 226 but logs instantiation errors for non-root
NOTIFY/M-SEARCH headers from real devices — rejected as a production trade.

### NOT RUN (disposition: cannot change a failed line gate)

| Gate | Status |
| --- | --- |
| Native build + native UDP/HTTP/expiry/shutdown | NOT RUN |
| Same-runtime jar/native bytes, startup, idle/scan-peak RSS, service memory, threads | NOT RUN |
| Installed boot/soak under `64m`/`128M` | NOT RUN |

### Changed files

- `docs/superpowers/specs/2026-10-09-library-simplification-design.md`: Task 5 decision
  section rewritten (feasibility corrected, line-gate rejection, `task5-router` link
  replaces unused `task5-protocol-factory`).
- Working tree restored: `build.gradle.kts` reverted; prototype sources deleted.

### Concerns

- Margin is one line; a different metadata policy (e.g. accepting error logs) would flip it.
- Footprint was never measured; a memory/thread argument against jUPnP is untested.

## FIXROUND2 — prove metadata on accepted root traffic

### Decision

**REJECT by the maintained SSDP source-line gate.** This decision does not
rely on excluding shared scheduling, and does not count reflection errors
from rejected non-root advertisements or ordinary M-SEARCH messages.
The accepted-root evidence below establishes the metadata requirement for
the archived candidate as written.

The decisive correction is that the old 21-entry estimate was unsupported.
`SsdpDiscovery.Gate.createReceivingAsync` checks raw headers before calling
`super.createReceivingAsync`, so messages rejected there do not exercise
lazy typed parsing. Conversely, accepted root NOTIFY messages can carry
extra known headers and duplicate values: the gate does not reject them.
When the protocol later calls a typed getter, `UpnpHeaders.parseHeaders()`
iterates every retained known header value, and `UpnpHeader.newInstance()`
tries matching classes through reflection. This is the path the test now
measures.

### Accepted-root fixture and agent evidence

The archived `SsdpDiscoveryTest.kt` now has
`registers_a_root_notification_with_extraneous_known_headers`. It sends two
root-alive NOTIFY messages through the actual adapter:

- The first has one root `NT` and `USN`, plus `ST: ssdp:all`,
  `USER-AGENT`, `CONTENT-TYPE`, `MAN`, `MX`, and `EXT`.
- The second has root values first, followed by additional validly parsed
  `NT` and `USN` type values.

Both datagrams assert a size below 641 bytes, matching jUPnP's 640-byte
default packet limit. Both roots are accepted and registered. The fixture
does not use an M-SEARCH or a non-root advertisement.

An isolated Linux/arm64 run in the existing GraalVM Community container used
`native-image-agent`. The fixture passed 1/1, and the agent recorded these
**21 distinct reflective header constructors**:

```text
ContentTypeHeader
DeviceTypeHeader
DeviceUSNHeader
EXTHeader
HostHeader
LocationHeader
MANHeader
MXHeader
MaxAgeHeader
NTEventHeader
NTSHeader
RootDeviceHeader
STAllHeader
ServerHeader
ServiceTypeHeader
ServiceUSNHeader
UDADeviceTypeHeader
UDAServiceTypeHeader
UDNHeader
USNRootDeviceHeader
UserAgentHeader
```

The complete agent output is in
[`reachability-metadata.json`](../../../.cache/task-5-evidence/agent-config-fixround2-final4/reachability-metadata.json).
The pinned jUPnP binary contains no bundled native-image metadata for these
classes. The existing project metadata stores one compact reflection entry
per physical line. The line comparison therefore remains:

| Item | Physical lines |
| --- | ---: |
| Replaced `SsdpCache.kt` + `SsdpMessage.kt` | 206 + 31 = 237 |
| Candidate adapter + moved `SsdpLookup` | 212 + 5 = 217 |
| Agent-proven jUPnP reflection constructors | 21 |
| Candidate total | **238 (+1)** |

This uses the smallest existing metadata entry format. It adds no blank
line or presentation-only padding. Counting only the nine ordinary fixture
classes would miss constructors reached by root messages that the candidate
currently accepts. The +1 result is not based on the earlier, unproven claim
that all 21 classes occur in ordinary SSDP traffic.

### SPI alternatives and remaining gates

The shared process-wide fair lock remains source-feasible and was exercised
by the two-session same-URL fixture. Per-interface address selection,
bounded executors, a null stream server, the root binder, and registry cap
also remain supported extension points. None is the rejection reason.
Replacing the release's concrete retrieval task without that serialization
would require protocol orchestration not exposed by a retriever factory;
that alternative was not needed to establish the line-gate result.

The final isolated JDK 17 run completed **13 tests successfully, 0 failed**.
It covered the 12 original prototype tests plus the new accepted-root
header fixture. The separate GraalVM agent run completed that fixture
**1/1** and recorded 21 header constructors.

```text
$ ./gradlew --offline compileTestKotlinJvm
BUILD SUCCESSFUL
w: SsdpDiscoveryTest.kt:416:51 Expression is unused.
```

The warning is in an existing archived test helper, not production code.
The full suite ran through the isolated Podman runner:

```text
$ bash .cache/task-5-evidence/run-isolated-jvm-test.sh
openjdk version "17.0.20.1"
[        13 tests successful      ]
[         0 tests failed          ]
```

The arm64 native executable build and native UDP/HTTP/expiry/shutdown run
were **NOT RUN** after the line gate failed. Same-runtime jar/native sizes,
startup, idle/peak RSS, service memory, live threads, and installed boot/soak
under unchanged `64m`/`128M` limits are **NOT RUN**. No native fallback,
memory-limit change, Task 6 adoption, or shared-board installation occurred.
The CDDL review remains applicable only to a future adoption.

One accidental host-side `./gradlew --offline jvmTest --tests
'*SsdpDiscoveryTest'` run bypassed the isolated Podman namespace, so its
result is invalid and was not repeated. It failed:
`ends_its_threads_and_a_pending_fetch_on_close`,
`admits_512_roots_rejects_the_513th_and_renews_admitted_roots`,
`keeps_a_renewed_root`,
`requests_nothing_for_a_host_name_https_cross_host_or_loopback_location`,
and `follows_no_redirect_external_entity_service_icon_embedded_device_or_url_base`.
These failures are not evidence. All cited fixture passes and metadata
observations above came from isolated Podman runs.

### Restoration and final state

The temporary `build.gradle.kts` source-set and jUPnP dependency changes
were removed with `apply_patch`. No candidate source, application wiring,
dependency, or native metadata was retained in production. The new fixture
remains only in the ignored archived prototype. The production backend and
runtime/JDK/Kotlin/heap settings are unchanged.

The design's Task 5 decision now rejects on evidence from the candidate's
actual accepted-root parsing path. It supersedes the prior unsupported
metadata rationale and the FIXROUND1 concern that the count depended on
ignoring ordinary non-root/M-SEARCH reflection errors.

Warnings-inclusive IntelliJ inspections on the actual worktree-qualified
design and report paths returned no problems. `git diff --check` passed;
the temporary Gradle dependency/source-set diff is empty.
