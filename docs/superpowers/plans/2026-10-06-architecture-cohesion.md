# Cohesive Components Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Netmon clear component ownership and explicit lifetimes while preserving its observable behavior.

**Architecture:** Keep one Kotlin Multiplatform project and the MQTT runtime boundary.
Extract policy from I/O, separate classification from presentation, and give each running component a resource owner.
Relocate packages only after those responsibilities are explicit.

**Tech Stack:** Kotlin Multiplatform 2.4.20, JVM toolchain 17, kotlinx serialization/coroutines,
Fritz2 1.0-RC21, MQTT.js/Paho, pytest through uv, Playwright WebKit, Go, Gradle, and Make.

**Spec:** [Cohesive components and explicit ownership](../specs/2026-10-06-architecture-cohesion-design.md).

## Global constraints

- Keep one Kotlin Multiplatform Gradle project. Separate Gradle modules are deferred.
- Preserve MQTT compatibility, persisted state, settings, discovery behavior, and display output.
- Keep the installed binary name and service command unchanged.
- Publication remains synchronous; changed-host events precede the scan event, which precedes state saving.
- Preserve the current initial nmap scan, normal scan, grace periods, restart floor, and changed-host definition.
- Do not change broker defaults, publish transport selection, retention semantics, host expiry, or identity trust order.
- Do not change device heuristics, discovery cadence, or state/topic network keys.
- No asynchronous publication, provenance tracking, state-schema versioning, new framework, or new dependency.
- Browser installation remains explicit through `make browser`.
- Keep existing Make command names and pytest marker names.
- Removing retained resource references and adding missing layout checks are deliberate improvements.
- Run one Gradle command at a time. Keep the IDE's Gradle build idle during CLI builds.
- Use an isolated execution workspace; leave the approved spec and plan branch intact.
- For every changed file, resolve or explain IDE inspection errors and warnings before committing.

## Review focus

Each condition below has an explicit test in its owning task.

1. An older state file lacks optional fields: it still loads with unchanged defaults and field names (Task 2).
2. The broker returns failure: scanning still follows the existing publication/save order and logs failure (Task 3).
3. A session fails midway through acquisition or teardown: every acquired resource is closed once (Task 5).
4. An app is cancelled while Fritz2 is mounting: no late rendering or subscriptions survive (Task 7).
5. Python tests are collected without WebKit installed: non-browser selections still collect and run (Task 9).

---

## File map and staging

Tasks 1-7 use current package locations so behavior changes remain separate from relocation.
Task 8 moves production and Kotlin test files using the mapping below.
New files from earlier tasks move with their owning package.
Tests mirror production package paths.

| Current owner                                                                                           | Final owner below `com/bkahlert/netmon`                                    |
|---------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `Host`, `Event`, `EventSource`, `IP`, `Cidr`, `Kind`, `Link`, `LinkSpeed`, `Status`, wire serialization | `contract` and `contract/serialization`                                    |
| `Application`, `SlicedApplication`, its state declarations                                              | `scanner/app`                                                              |
| `scanner`, excluding persistence implementation                                                         | `scanner/scan`                                                             |
| `identity`                                                                                              | `scanner/identity`                                                         |
| `mdns`, `ssdp`, `router`, `enrichment/LockdownProbe`                                                    | `scanner/discovery/mdns`, `ssdp`, `router`, `lockdown`                     |
| `Enricher`, `HostPropertyEnricher`, `HostServicesEnricher`                                              | `scanner/scan/enrichment`                                                  |
| `nmap`, `mqtt` JVM packages                                                                             | `scanner/nmap`, `scanner/mqtt`                                             |
| `net`, `exec`, `xml`, `logging`, JVM cache/PID helpers                                                  | `scanner/support/net`, `exec`, `xml`, `logging`, `cache`, `process`        |
| Scanner state-file store                                                                                | `scanner/state`                                                            |
| `main`, `app`, build version                                                                            | `display/app`                                                              |
| JS `ui/events`, MQTT bindings                                                                           | `display/broker` and `display/broker/mqtt`                                 |
| Scan store, `HostGroup`, `OnlineAge`, elapsed-time presentation, host/scan rendering                    | `display/networks`                                                         |
| Kiosk stats/store, OTLP, metrics formatting                                                             | `display/metrics` and `display/metrics/otlp`                               |
| Model descriptions/icons, URI helpers, icon rendering and symbol declarations                           | `display/presentation` and its `uri`/`icons` children                      |
| Clock/console stores, remaining browser/console/render helpers                                          | `display/support`                                                          |
| Generic config/text/byte/unquoted-string helpers                                                        | `support/config`, `support/text`, `support/bytes`, `support/serialization` |

Split mixed files rather than moving them whole.
`stores.kt` is replaced by feature-owned files.
`ScanResult` remains scanner-owned; it is not the MQTT scan-event DTO.
`IP` actual implementations remain in platform source sets under the shared contract package.
Remove unused JVM presentation loaders after their tests have moved to the display.

Use the existing source-set resource roots.
The new scanner resource is `src/jvmMain/resources/assets/model-catalog.json`.
Existing presentation JSON moves to `src/jsMain/resources/assets` without changing packaged asset paths.

Each task ends with a focused commit after its checks.
Stage only that task's files; never use `git add -A` to collect unrelated changes.
Suggested commit messages appear below.

### Task 1: Capture behavior before extraction

**Files:** Create `src/jvmTest/kotlin/com/bkahlert/netmon/identity/ModelCatalogBaselineTest.kt`
and `src/jvmTest/resources/assets/model-catalog-baseline.json`.
Create `src/commonTest/kotlin/com/bkahlert/netmon/serialization/JsonFormatTest.kt`;
extend `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/ScanResultTest.kt`.
Create `tests/test_architecture.py` for dependency and artifact guards used in later tasks.

**Interfaces:** The baseline contains every current model identifier and the current nullable `AppleCodes.kindOf` result.
It is test data, never a runtime fallback.

- [ ] Capture the baseline from the unchanged classifier and checked-in asset.
  Review the records, including unclassified models and custom non-Apple-shaped identifiers.
- [ ] Add passing characterization tests named `every_shipped_model_matches_the_recorded_baseline`
  and `wire_fields_and_tokens_are_stable`.
  Assert exact catalog membership/kinds and decoded JSON field names, epoch seconds, omitted nulls,
  unknown-field handling, unknown-kind fallback, and scan/host discriminator values.
  Store action results before assertions.
- [ ] Add a state fixture lacking `lastSeen`, `kind`, `link`, and `speed`.
  `an_older_state_file_loads_with_optional_fields_absent` asserts successful loading and null defaults.
  Preserve existing merge-table assertions rather than replacing them.
- [ ] Run `./gradlew --no-daemon --console=plain jvmTest -PunitOnly --tests '*ModelCatalogBaselineTest' --tests '*JsonFormatTest' --tests '*ScanResultTest'`.
  Expected: all selected tests pass against unchanged production code.
  Characterization tests deliberately start green; later behavior tests start red.
- [ ] Commit as `test(architecture): capture refactoring compatibility baseline`.

### Task 2: Separate merge policy and state persistence

**Files:** Modify `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt`
and `NetmonScanner.kt`.
Create sibling `MergeResult.kt`, `ScanStateStore.kt`, and `JsonScanStateStore.kt`.
Create `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/JsonScanStateStoreTest.kt`;
modify `ScanResultTest.kt` and existing integration-test persistence callers.

**Interfaces:**

- `data class MergeResult(val scan: ScanResult, val changedHosts: List<Host>)`.
- `ScanResult.merge(currentResult: ScanResult, downAfter: Duration, notBefore: Instant): MergeResult`.
- `interface ScanStateStore` with `fun load(): ScanResult?` and `fun save(scan: ScanResult): Unit`.
- `JsonScanStateStore(file: Path, format: StringFormat = JsonFormat): ScanStateStore`.

- [ ] Add red tests: `merge_returns_changes_without_invoking_external_code`,
  `changes_keep_the_existing_pairing_order`, and `identity_only_updates_have_no_host_event`.
  Assert the complete next scan and the changed-host list separately.
- [ ] Run `./gradlew --no-daemon --console=plain jvmTest -PunitOnly --tests '*ScanResultTest' --tests '*JsonScanStateStoreTest'`.
  Expected red: the new result/store API does not exist.
- [ ] Extract file operations unchanged into `JsonScanStateStore`.
  Bind the path once; retain adjacent temporary files, atomic replacement, logging,
  decode-failure fallback, and propagated file-read errors.
  Replace callback emission in merge with collecting the same hosts in the same order.
- [ ] Update `NetmonScanner` to publish returned changes, publish the scan, then save it.
  Keep all other constructor behavior until Task 3.
  Update direct callers and tests; remove `ScanResult.load/save` rather than retaining competing APIs.
- [ ] Add store tests: old fixture loads; two writes replace the same file; unreadable JSON logs
  and returns null; a file-read exception propagates; failed/interrupted saves keep their logging policy.
  Use a temporary directory and existing log assertions, not a live application directory.
- [ ] Rerun the selected tests. Expected: pass, including Task 1 compatibility fixtures.
- [ ] Commit as `refactor(scanner): separate merge results from file persistence`.

### Task 3: Make the scan cycle testable without nmap

**Files:** Modify `scanner/NetmonScanner.kt`, `Application.kt`, and `nmap/NmapNetworkScanner.kt`.
Create `scanner/NetworkContext.kt`, `scanner/NetworkScan.kt`, and
`nmap/NmapScanAdapter.kt`, plus `mqtt/ScannerEventPublisher.kt`.
Create `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/NetmonScannerTest.kt`
and `src/jvmTest/kotlin/com/bkahlert/netmon/nmap/NmapScanAdapterTest.kt`,
plus `src/jvmTest/kotlin/com/bkahlert/netmon/mqtt/ScannerEventPublisherTest.kt`.

**Interfaces:**

- `data class NetworkContext(val interfaceName: String, val cidr: Cidr)`.
- `enum class ScanMode { INITIAL, NORMAL }`.
- `fun interface NetworkScan { fun scan(network: Cidr, mode: ScanMode): List<Host> }`.
- `NmapScanAdapter(run: (Cidr, TimingTemplate) -> List<Host>): NetworkScan`.
  Production supplies `nmapNetworkScanner::scan`.
- `ScannerEventPublisher(publisher: Publisher<Event>, scanTopic: String, hostTopic: String)`
  exposes `fun publishScan(scan: ScanResult): Unit` and `fun publishChange(host: Host): Unit`.
  It owns the current event mapping and logs unsuccessful publication without throwing.
- `NetmonScanner(context: NetworkContext, scanner: NetworkScan, enrichers: List<Enricher<Host>>,
  state: ScanStateStore, clock: Clock, downAfter: Duration,
  onScan: (ScanResult) -> Unit, onChange: (Host) -> Unit)`.
  Retain one private `RestartFloor` per scanner and public `fun scan(): Unit`.

- [ ] Add red tests using operation-recording fakes.
  With existing state, assert `load, normal scan, enrichment, host publications, scan publication, save`.
  Without state, assert one initial scan before the normal scan.
  Initial hosts remain unenriched, and both scan timestamps use the supplied clock.
- [ ] Add `a_failed_publish_does_not_skip_saving_current_state`.
  A fake publisher returning false is used through `ScannerEventPublisher` and its actual callbacks.
  Assert remaining publications and saving still happen, with failure logging.
  Also assert a thrown scan exception prevents publication/save and propagates.
- [ ] Run
  `./gradlew --no-daemon --console=plain jvmTest -PunitOnly --tests '*NetmonScannerTest' --tests '*NmapScanAdapterTest' --tests '*ScannerEventPublisherTest'`.
  Expected red: required interfaces/constructor are absent.
- [ ] Implement the interfaces and orchestration.
  Bind state-file paths and read settings at composition.
  Use `TimingTemplate.Insane` for INITIAL and `TimingTemplate.Aggressive` for NORMAL.
  Do not change nmap privilege fallback, retries, or XML parsing.
- [ ] Test the adapter's two modes using the supplied function; neither test starts nmap.
  Rerun selected tests and Task 2 tests. Expected: pass.
- [ ] Commit as `refactor(scanner): make scan orchestration independent of adapters`.

### Task 4: Split classification and presentation catalogs

**Files:** Create `src/jvmMain/kotlin/com/bkahlert/netmon/identity/ModelCatalog.kt`
and `src/jvmMain/resources/assets/model-catalog.json`.
Modify `identity/AppleCodes.kt`, `Application.kt`, `tests/device_model_codes.py`,
and their tests.
Move both JSON presentation assets into `src/jsMain/resources/assets`.
Remove scanner usage of `model_identification/DeviceModelCodes.jvm.kt`.

**Interfaces:**

- `interface ModelCatalogLookup : Set<String> { fun kindOf(modelCode: String): Kind? }`.
- Serializable `ModelCatalog` holds `models: Map<String, Kind?>`; it implements that lookup.
- `fun loadModelCatalog(): ModelCatalog` loads the JVM resource with the existing wire JSON policy.
- `AppleCodes(codes: ModelCatalogLookup)` retains its current public methods.
- Python `build_catalog(model_codes: Iterable[str], existing: dict) -> dict`
  returns `{"models": {code: nullable_kind_token}}`.
  Presentation generation retains its existing `build(index, svgs)` interface.

- [ ] Seed runtime classification from Task 1's baseline.
  Do not regenerate expected results after changing the classifier.
- [ ] Add red tests: `classification_uses_explicit_kind_without_a_symbol`,
  `presentation_symbols_do_not_change_classification`, and `new_models_have_no_implicit_kind`.
  Assert preserved acceptance/normalization and null classification for previously unclassified codes.
- [ ] Add generator tests: changed SVGs leave catalog output unchanged;
  existing explicit kinds survive regeneration; newly recognized codes get null.
  Membership follows generated model identifiers, not symbol availability.
- [ ] Run `./gradlew --no-daemon --console=plain jvmTest -PunitOnly --tests '*AppleCodesTest' --tests '*ModelCatalogBaselineTest'`
  and `uv run --frozen pytest tests/test_device_model_codes.py -q`.
  Expected red for the new catalog API and generator behavior.
- [ ] Replace symbol-family classification with explicit lookup.
  Keep Apple shape checks, OUI/private-MAC acceptance, Linux rejection, and suffix normalization.
  Update generator destinations and remove presentation resources from JVM loading.
- [ ] Move presentation-only resource tests into `jsTest`; keep generator integrity checks in Python.
  Assert the browser still fetches `assets/device-model-codes.json` and `assets/device-icons.json`
  through the existing hashed distribution pipeline.
- [ ] Rerun selected JVM/Python tests, then `make test-js` and `make test-layout`.
  Expected: baseline matches, no lost icons/descriptions, unchanged geometry.
- [ ] Commit as `refactor(identity): decouple classification from presentation assets`.

### Task 5: Give network sessions resource ownership

**Files:** Create `src/jvmMain/kotlin/com/bkahlert/netmon/NetworkSession.kt`
and `NetworkResources.kt`.
Modify `Application.kt`, `SlicedApplication.kt`, `mdns/JmDNS.kt`, and `mqtt/MqttPublisher.kt`.
Create matching `NetworkSessionTest.kt` and `NetworkResourcesTest.kt`;
extend `ApplicationTest.kt` and `SlicedApplicationTest.kt`.

**Interfaces:**

- `NetworkResources : AutoCloseable` exposes `fun <T : AutoCloseable> own(resource: T): T`.
  It closes owned resources in reverse acquisition order, once.
- `NetworkSession.open(createScanner: (NetworkResources) -> NetmonScanner): NetworkSession`.
  The private session owns that scanner and resource set.
  It exposes `fun scan(): Unit` and `override fun close(): Unit`.
- `MqttPublisher<T>` also implements `AutoCloseable`; closing disconnects and closes its Paho client.
- The existing `SlicedApplication` scheduling API remains; shutdown-hook registration moves to application ownership.

- [ ] Add red tests with counted resources:
  partial acquisition closes earlier resources; successful close is reversed;
  a failing close does not skip later closes; repeated close is inert.
  Preserve the primary failure and attach cleanup failures as suppressed exceptions.
- [ ] Run `./gradlew --no-daemon --console=plain jvmTest -PunitOnly --tests '*NetworkResourcesTest' --tests '*NetworkSessionTest' --tests '*ApplicationTest'`.
  Expected red: session/resource APIs are absent.
- [ ] Implement resource ownership used by actual production startup.
  Register JmDNS, its cache, SSDP listener, and router table as acquired.
  Explicitly close both cache and JmDNS; the cache's close does not close its instance.
  Report cleanup failures at the existing application error boundary.
- [ ] Replace five maps with one map of sessions keyed by the same slice value the manager schedules.
  Preserve topic substitution, interface selection, source order, and worker-failure propagation.
  Change service enrichment to use `MdnsLookup`.
- [ ] Remove factory-level JmDNS and manager-level shutdown registrations.
  Application registers one shutdown owner, stops/joins workers before closing the publisher,
  and removes its hook after normal termination.
  Normal, failed, and shutdown termination share idempotent cleanup.
- [ ] Add worker tests with an immediate controllable signal:
  slice disappearance closes once; reappearance creates fresh resources;
  publisher closes after workers; startup/cleanup exceptions remain observable.
  Do not use a test-only path bypassing production acquisition.
- [ ] Rerun selected tests; run `SlicedApplicationTest` explicitly without `-PunitOnly`
  because that property excludes it.
  Expected: lifecycle assertions pass without new sleeps.
- [ ] Commit as `refactor(scanner): centralize session and shutdown ownership`.

### Task 6: Make console interception reversible

**Files:** Modify `src/jsMain/kotlin/com/bkahlert/kommons/js/tee.kt`,
`OnScreenConsole.kt`, and console store wiring in `stores.kt`.
Create `src/jsTest/kotlin/com/bkahlert/kommons/js/ConsoleSubscriptionTest.kt`.

**Interfaces:**

- `interface ConsoleSubscription { fun dispose(): Unit }`.
- `fun Console.observe(levels: List<String>, observer: (String, Array<dynamic>) -> Unit): ConsoleSubscription`.
- `OnScreenConsole` gains `fun dispose(): Unit`.
  `disable()` retains the current visual fade; disposal additionally releases interception and timers.
- Keep a Flow adapter for store collection; cancellation disposes its subscription.

- [ ] Add red tests: one original console invocation per call; two listeners receive independently;
  disposing either listener stops only that listener; disposing the last restores original methods.
  Repeated disposal must not restore stale wrappers.
- [ ] Run `make test-js`. Expected red for missing reversible subscriptions.
- [ ] Replace stacked permanent wrappers with one dispatcher per observed console method.
  Restore the original function when its last observer leaves.
  Preserve original `this`, arguments, return value, and normal console output.
  Confine dynamic interop to the existing console boundary.
- [ ] Make the onscreen console cancel its timers and remove its container on disposal.
  Preserve its enable/disable animation during ordinary startup.
- [ ] Rerun `make test-js`. Expected: new and existing console/render tests pass.
- [ ] Commit as `refactor(display): release console subscriptions on disposal`.

### Task 7: Own display state and rendering lifetimes

**Files:** Replace `stores.kt` with `CurrentTimeStore.kt`, `ScanEventsStore.kt`,
`KioskStatsStore.kt`, `ConsoleLogStore.kt`, and `HostsLens.kt` in the current package.
Modify `app.kt`, `main.kt`, `ui/network.kt`, and `ui/events.kt`.
Create `DisplayApp.kt`, `ui/OwnedRender.kt`, and `src/jsTest/kotlin/com/bkahlert/netmon/DisplayAppTest.kt`.
Update existing store tests and the Fritz2 test helper.

**Interfaces:**

- `CurrentTimeStore(refreshInterval: Duration, clock: Clock, job: Job)`.
  Remove companion/singleton clocks; composition creates fast and minute clocks.
- `ScanEventsStore(outdatedThreshold: Duration, clock: Clock, ticks: Flow<Instant>, job: Job)`.
- `KioskStatsStore` retains payload/interval/clock inputs but requires an owned job.
  Console state also requires an owned job.
- `suspend fun renderOwned(target: HTMLElement, content: RenderContext.() -> Unit): OwnedRender`.
  `OwnedRender` exposes `suspend fun dispose(): Unit`.
- `DisplayApp` exposes `suspend fun dispose(): Unit`.
- `suspend fun app(statusTarget: HTMLElement, networksTarget: HTMLElement,
  messages: Flow<MqttMessage>? = null, clock: Clock = Clock.System, onSuccess: () -> Unit = {}): DisplayApp`.
  Null messages selects the production broker flow once inside app composition.
  `main` resolves current selectors and invokes this entry point.

**Pinned-library constraint:** Fritz2 1.0-RC21's top-level `render` returns Unit and creates its own root Job.
It does not accept a parent Job. Its `RootStore` accepts an owned Job.
Capture the render context's job inside the render callback.
Do not invent a `render(job = ...)` API or replace Fritz2.

- [ ] Add red tests with controlled flows/clocks:
  one shared connection; current freshness thresholds; fast-to-minute handoff;
  disposal stops state and DOM updates; a second instance has no predecessor subscriptions.
- [ ] Add `cancellation_during_mount_leaves_no_late_content`.
  Cancel the suspending mount before its callback runs.
  When that callback runs, cancel its root Job and do not execute content.
  Assert the target remains empty and the broker has no collector.
- [ ] Run `make test-js`. Expected red for missing owned-app/render APIs.
- [ ] Implement owned rendering using the actual callback Job.
  Disposal cancels/joins root rendering jobs, then app-owned jobs, releases console interception,
  and clears only the app's two render targets.
  If mounting the second root fails, clean up the first and app resources.
- [ ] Supply owned jobs and clocks to all stores.
  Pass clocks through scan/card rendering instead of reading singleton stores.
  Keep status/scan rendering subscriptions and startup callback behavior unchanged.
- [ ] Make `mqttMessageFlow` cancellation close its client.
  Keep sharing inside the app lifetime; preserve subscriptions, QoS, payload filtering and decode logging.
- [ ] Replace the test helper's detached jobs with one cancelled test lifetime.
  Use explicit signals/frames or coroutine test clocks; do not add arbitrary delays.
- [ ] Rerun `make test-js`, then `make test-layout`.
  Expected: lifecycle tests pass and current DOM/geometry/style assertions remain unchanged.
- [ ] Commit as `refactor(display): own application state and render lifetimes`.

### Task 8: Relocate packages and enforce dependency direction

**Files:** Move Kotlin production/tests according to the file map.
Modify `build.gradle.kts`, native-image resource files, and all imports/resource references.
Create `src/commonMain/kotlin/com/bkahlert/netmon/contract/ScanTopics.kt`.
Extend `tests/test_architecture.py`.

**Interfaces:**

- Final scanner entry point: `com.bkahlert.netmon.scanner.app.Application`.
- Keep `Host`, events, address types, tokens, and wire serializers under `contract`.
- Move `Kind.label` to a display-owned extension; serialized token handling remains in `Kind`.
- Scanner `BrokerSettings` belongs to `scanner/app`; browser settings belong to `display/app`.
- `ScanTopics` under `contract` constructs/parses the existing topic templates supplied by callers.
  Scanner owns host/scan topic settings; display owns its scan subscription setting and freshness settings.
  Preserve setting keys `scan.topic`, `host.topic`, `scan.datedThreshold`, and `scan.outdatedThreshold`.
- `ScanTopics.topic(template: Template, source: EventSource): String`,
  `ScanTopics.subscription(template: Template): String`, and
  `ScanTopics.pattern(template: Template): Regex` are pure operations.
  `EventSource.fromTopic(topic: String, template: Template): EventSource` takes the template explicitly.
  Remove `EventSource.PATTERN`'s global settings dependency; broker decoding uses the configured pattern.
- Generic `Settings(name: String? = null, defaultFormat: StringFormat)` receives its default format explicitly.
  Application settings supply the existing unquoted wire format; support has no Netmon import.

- [ ] Add red dependency checks:
  no scanner/display cross-imports; contract imports neither component nor settings;
  shared support imports no component or contract policy.
  Add a wiring check that Gradle and the manifest name the new scanner entry point.
- [ ] Run `uv run --frozen pytest tests/test_architecture.py -q`.
  Expected red against the current back-edges and entry-point declaration.
- [ ] Apply the complete package mapping with IDE refactoring where supported.
  Split scan metadata from `network.kt` only where needed to move ownership.
  Put status presentation under display support, not broker infrastructure.
  Move UI-only grouping/age tests from common tests to JS tests.
- [ ] Remove support dependencies on logging/JSON policy.
  JVM cache helpers move under scanner support; generic shared helpers remain independent.
  Compose scanner-specific interface predicates in application startup.
  Preserve platform setting origins/defaults and event-topic parsing behavior.
- [ ] Update main-class declarations, native metadata, resource imports, Kotlin tests,
  webpack/Tailwind source paths, and distribution wiring.
  Match moved files to their actual UI dependencies: render/context helpers stay display-owned,
  while generic text/byte helpers remain shared.
  Move presentation-only common tests to `jsTest`, including model/icon lookup and display-label tests.
  Contract serialization tests remain common; scanner model-catalog tests remain JVM-only.
  Keep binaries, jar names, Make targets, and packaged URLs unchanged.
- [ ] Run `make test-jvm`, then `make test-js`, then `make test-layout`,
  then the architecture checks. Expected: pass with no legacy production package duplicates.
- [ ] Commit as `refactor(architecture): organize packages by component ownership`.

### Task 9: Separate Python tooling from test boundaries

**Files:** Create `tools/netmon_dev/__init__.py` and feature packages `preview`, `bench`, `assets`, `system`.
Move current developer/support files from `tests`.
Move tests into `tests/unit`, `tests/browser`, and `tests/system`.
Modify `pyproject.toml`, root/package conftests, `Makefile`, `.run` configurations, and active command references.
Move `test_architecture.py` to `tests/unit/test_architecture.py`.

**Interfaces:**

- Commands: `python -m netmon_dev.preview`, `netmon_dev.preview.broker`,
  `netmon_dev.bench`, `netmon_dev.assets.device_model_codes`, `netmon_dev.assets.device_icons`,
  and `netmon_dev.system.vm_device`.
- Shared scan definitions: `netmon_dev.preview.scan_fixtures`.
- Shared boot/soak/apt helpers: `netmon_dev.system.booted`, `sampling`, `aptprobe`.
- Test-only browser support: `tests/browser/layout.py`, imported as `tests.browser.layout`.
- Add package initializers for test directories.
  Set pytest `pythonpath = [".", "tools"]`, retain `--import-mode=importlib`,
  and remove `pythonpath = ["tests"]`.
  Keep uv's non-distribution project configuration.

**Exact Python moves:**

| Current file below `tests`                                             | Destination below `tools/netmon_dev`                                                  |
|------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `preview.py`, `preview_broker.py`, `scan_fixtures.py`                  | `preview/__main__.py`, `preview/broker.py`, `preview/scan_fixtures.py`                |
| `bench.py`, `bench_figures.py`, `bench_fixtures.py`, `bench_report.py` | `bench/__main__.py`, `bench/figures.py`, `bench/fixtures.py`, `bench/report.py`       |
| `device_model_codes.py`, `device_icons.py`                             | `assets/device_model_codes.py`, `assets/device_icons.py`                              |
| `booted.py`, `sampling.py`, `aptprobe.py`, `vm_device.py`              | `system/booted.py`, `system/sampling.py`, `system/aptprobe.py`, `system/vm_device.py` |

Move `test_layout.py` and `layout.py` to `tests/browser`.
Move `test_boot.py`, `test_display.py`, `test_soak.py`, and `test_apt.py` to `tests/system`.
All remaining root `test_*.py` files move to `tests/unit`.
Keep package-local tests in place.
Put importable preview adapter declarations in `preview/__init__.py`;
its `__main__.py` only invokes the command.
For benchmarks, put importable command logic in `bench/command.py`;
its `__main__.py` only invokes that logic.
Fix path calculations formerly based on `parents[1]` and all subprocess paths during these moves.

- [ ] Add red tests pinning Make dry-run output to the new module entry points.
  Add import checks executed from the repository root and marker/collection selection tests.
- [ ] Run `uv run --frozen pytest tests/test_makefile.py tests/test_conftest.py -q`.
  Expected red for old command paths and filename-based browser requirements.
- [ ] Move files with imports updated atomically.
  Preview/benchmark commands retain environment variables, CLI options, return codes,
  output paths, and behavior.
  Retain one scan-fixture definition shared by commands and browser tests.
- [ ] Mark layout and installed-display browser tests with `requires_webkit`.
  Register that marker and check it after selection in the root collection hook.
  Missing WebKit still fails before VM startup, including during collect-only.
  Remove filename-based requirements; keep fixture-level checks as actionable launch errors.
  Keep layout/boot/soak/apt/installed/tier0 markers and target rules intact.
- [ ] Test non-browser selection with browser availability forced false:
  tier0/unit collection succeeds; selecting browser tests gives the existing actionable installation error.
  The browser requirement must not cause a test to run when only a different marker was selected.
  Do not boot a VM merely to check collection.
- [ ] Run `uv run --frozen pytest tests/unit -m tier0 -q`, `make test-preview`,
  and `make test-layout`. Expected: relocated tests and module commands work.
  Preview tests require the existing Podman setup; report its absence rather than skipping silently.
- [ ] Commit as `refactor(tooling): separate developer commands and test boundaries`.

### Task 10: Make documentation and aggregate validation match ownership

**Files:** Create `docs/architecture.md`, `scanner.md`, `display.md`, `mqtt-contract.md`.
Replace `docs/how-it-works.md` with navigation.
Modify `README.md`, `docs/open-issues.md`, `Makefile`, `.github/workflows/ci.yml`,
`.github/workflows/release.yml`, and relocated Make/static tests.
Create `tests/unit/test_documentation.py`.

**Interfaces:** Existing public commands remain.
`test` gains `test-layout`; `test-all` inherits it.
CI/release install WebKit explicitly before layout validation.

- [ ] Add red checks:
  Make dry-run for `test` and `test-all` includes layout;
  CI/release invoke browser installation and production-bundle layout validation.
  Local links in current maintainer docs resolve.
- [ ] Run `uv run --frozen pytest tests/unit/test_makefile.py tests/unit/test_documentation.py -q`.
  Expected red for missing coverage and new documentation destinations.
- [ ] Move existing documentation by owner.
  Architecture includes the source map, startup/shutdown ownership, exact scan order,
  display flow, metrics boundary, and test boundary table.
  Scanner/display extension instructions point to their new packages and tests.
  MQTT documentation retains exact topics, optional fields, retention, QoS, and compatibility.
- [ ] Keep the old page as a navigation landing page.
  Fix current README/open-issue/source links without rewriting historical plans/specs.
  Historical links must be identified as historical, not presented as current instructions.
- [ ] Add layout to aggregate targets and release validation.
  Avoid concurrent Gradle tasks, including when Make is invoked with parallel scheduling:
  group JVM/JS/distribution invocations through one ordered recipe or prerequisite chain.
  Do not serialize unrelated Go/Python checks unnecessarily.
- [ ] Rerun the selected Python checks. Expected: pass, with valid current documentation links.
- [ ] Commit as `docs(architecture): document ownership and align validation coverage`.

### Task 11: Validate the integrated artifacts and hand off

**Files:** No new production files. Fix only regressions introduced by Tasks 1-10.
Update the current documentation if validation exposes inaccurate commands.

**Interfaces:** All spec acceptance rows map to Tasks 1-10.
The final artifact check confirms composition, not just individual helper behavior.

- [ ] Run `make test-jvm`, `make test-js`, and `make test-metrics` sequentially.
  Expected: all suites pass; no compatibility fixture changes to hide differences.
- [ ] Run `make test-layout`, then `make test-tier0`.
  Expected: built page geometry/style/assets and architecture/tooling/documentation checks pass.
- [ ] Run `make build`, then `make test-tier1`.
  Expected: native scanner starts through the new entry point; display files and package scripts
  retain their installed locations.
  Building requires the existing Podman/native-image environment.
- [ ] Run `make test-tier2` with the existing VM setup.
  Expected: scanner-to-MQTT-to-WebKit display flow shows the gateway and optional kiosk metrics.
  Preserve the page and kiosk screenshots as existing output artifacts.
- [ ] Check IDE inspections for all touched files and `git diff --check`.
  Review package-direction assertions, classification baseline, lifecycle cleanup,
  artifact names, and the explicit exclusions against the spec.
- [ ] Record any unavailable environment-dependent check as blocked, not passed.
  Obtain the required artifact evidence before calling implementation complete.
  Commit only regression fixes, then request whole-branch review.

## Coverage and execution handoff

| Spec area                                              | Tasks   |
|--------------------------------------------------------|---------|
| Package ownership and dependency direction             | 8       |
| Classification/presentation separation                 | 1, 4    |
| Pure merge, state compatibility, scan ordering         | 1, 2, 3 |
| Scanner sessions and shutdown ownership                | 5       |
| Display lifetime and console cleanup                   | 6, 7    |
| Tooling, browser/system boundaries and marker behavior | 9       |
| Current documentation and aggregate/release validation | 10      |
| Runtime and installed artifact preservation            | 8, 11   |

The tasks form one migration chain; do not run same-worktree implementations in parallel.
Tasks 4 and 6 can be developed independently only in separate workspaces with a coordinated merge.
No parallel execution is required.

Review this plan before implementation and select an execution method.
Native execution is recommended because most tasks share interfaces and migration state.
Use one independent whole-branch review afterward.
Subagent-driven execution remains available for per-task independent review at higher context cost.
