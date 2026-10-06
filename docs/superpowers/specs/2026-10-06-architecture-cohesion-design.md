# Cohesive components and explicit ownership

## Intent and scope

Make Netmon easier to navigate, understand, and change without increasing runtime coupling.
A maintainer should find a behavior's owner without following unrelated frontend or backend code.
Startup, scan ordering, resource lifetimes, and test boundaries should be explicit.

The user approved a package-first refactoring on 2026-10-06.
Keep one Kotlin Multiplatform Gradle project.
Separate Gradle modules are deferred.
This document specifies the refactoring, not permission to implement it.

The work covers package ownership, model classification, scan orchestration, resource lifetimes,
developer tooling, test organization, and maintainer documentation.
Preserve MQTT compatibility, persisted state, settings, discovery behavior, and display output.
Removing retained resource references and adding missing layout checks are deliberate improvements.

## Current boundaries and problems

The JVM scanner and JS display communicate through MQTT.
The optional Go metrics sampler publishes independently.
Keep these runtime boundaries and the existing Debian package boundaries.

| Surface                                                                                                                                               | Current problem                                                                                                 |
|-------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| [AppleCodes](../../../src/jvmMain/kotlin/com/bkahlert/netmon/identity/AppleCodes.kt)                                                                  | Backend classification derives device kinds from frontend SF Symbol names.                                      |
| [ScanResult](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt)                                                                   | One type owns state transitions, change callbacks, serialization, and filesystem access.                        |
| [NetmonScanner](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/NetmonScanner.kt)                                                             | Orchestration depends directly on nmap, interface discovery, global settings, clocks, and static persistence.   |
| [Application](../../../src/jvmMain/kotlin/com/bkahlert/netmon/Application.kt)                                                                         | Five parallel maps distribute ownership of each network's resources.                                            |
| [Frontend events](../../../src/jsMain/kotlin/com/bkahlert/netmon/ui/events.kt) and [stores](../../../src/jsMain/kotlin/com/bkahlert/netmon/stores.kt) | Transport lives under UI; unrelated stores and independently created jobs obscure app lifetimes.                |
| [Python tests](../../../tests)                                                                                                                        | Tests, browser support, preview commands, benchmarks, and asset generators share one directory and import path. |
| [How it works](../../how-it-works.md)                                                                                                                 | A scanner document includes frontend rendering, styling, and asset-generation details.                          |

The package tree also contains dependency back-edges.
Network-interface discovery reads scanner settings.
Generic-looking `kommons` helpers depend on Netmon's logging and JSON policy.
Moving files alone does not resolve these dependencies.

## Approach

Choose package ownership and targeted extraction before build-module separation.
This exposes boundaries while keeping packaging and build changes mechanical.

Documentation-only cleanup would improve navigation but leave the coupling intact.
A Gradle module split would enforce boundaries sooner, but combine architectural and build migrations.
Neither is the selected approach.

Extract only responsibilities with an actual independent owner or external dependency.
Do not introduce a framework, dependency-injection container, event bus, or configurable plugin system.
Do not create interfaces for every class.

## Ownership and dependency direction

Use `com.bkahlert.netmon` as the package root.
The following tree describes logical ownership across source sets, not additional Gradle modules.
Existing Kotlin expect/actual declarations remain paired.

```text
contract/                         commonMain: host/event/address values and wire serialization
scanner/                          jvmMain
  app/                            composition, worker management, network-session ownership
  scan/                           scan orchestration, merge rules, restart floor and scan settings
  identity/                       clues, resolution rules and classification catalog
  discovery/
    mdns/                         mDNS lookup, cache and adapter
    ssdp/                         SSDP lookup, listener and description fetching
    router/                       router lookup, cache and TR-064 adapter
    lockdown/                     bounded lockdownd lookup and probe
  nmap/                           command execution adapter and XML-to-host conversion
  state/                          JSON state-file persistence
  mqtt/                           retained event publishing
  support/                        JVM network, process, XML and logging helpers
display/                          jsMain
  app/                            startup and app-instance lifetime
  broker/                         MQTT connection, subscription and message decoding
  networks/                       scan state, grouping, cards and elapsed-time presentation
  metrics/                        OTLP decoding, kiosk state and status presentation
  presentation/                   model descriptions, icons and URI helpers
  support/                        browser clocks, console and rendering helpers
support/                          genuinely shared configuration/text/byte helpers
```

The dependency rules are:

- `contract` does not depend on scanner, display, configuration, or presentation.
- Scanner and display depend on `contract`, never on each other's implementation.
- Scanner composition selects concrete adapters and policies.
  Merge and identity resolution do not perform network or filesystem I/O.
- Display composition connects broker flows to feature state and rendering.
  Broker code does not depend on rendering.
- Shared support does not depend on application-owned logging or wire-format policy.

Move `HostGroup`, online-age styling thresholds, and display labels into display ownership.
Keep serialized kind tokens and compatibility handling in `contract`.
Keep scanner and display broker settings separate; share only helpers both actually use.
Separate scan-topic construction from display freshness settings.
Pass the wire JSON format where required rather than making generic settings depend on it.

Compose scanner-specific network predicates outside the system interface resolver.
Use the existing lookup abstractions for enrichment.
In particular, service enrichment depends on `MdnsLookup`, not `JmDNSServiceInfoCache`.
Keep protocol record adapters beside the corresponding discovery implementation.

Package moves must update tests, Gradle entry points, the jar manifest, native-image metadata,
and resource references together.
Keep the installed binary name and service command unchanged.
Do not leave a second obsolete package hierarchy as a permanent compatibility facade.

## Classification and presentation assets

The scanner owns a catalog of recognized model identifiers and their optional device kinds.
The display owns descriptions, symbol selection, and SVG content.
No scanner classification reads a symbol name or SVG.

Seed explicit catalog records from the current shipped classification results once during migration.
Preserve recognition, color-suffix normalization, Apple-code acceptance, and every current kind result.
Models that currently have no classified kind retain that result.
Record the baseline before changing the classifier; do not derive it from the new implementation.

The scanner catalog belongs in JVM resources.
Presentation assets belong in JS resources.
Their schemas and loaders are separate, even if one developer command regenerates both.
The scanner must not load presentation assets to recognize or classify a model.

Regeneration updates model recognition without inferring kinds from symbols.
Existing explicit classifications are retained.
A newly recognized model has no catalog kind until explicitly classified.
This retains the current ability to recognize a model without knowing its kind.
Neither a changed icon nor a missing icon changes classification.

Preserve current descriptions, icon precedence, fallback glyphs, and packaged asset URLs.
Generation remains a developer command, not a runtime or ordinary build requirement.
No external download is needed to consume the checked-in catalogs.

## Scan orchestration and state

Keep `NetmonScanner` as the scan orchestrator.
Supply it with the network context, scan operation, enrichers, state store, event delivery,
clock, and scan policy it actually uses.
Do not let it rediscover its interface or choose an implicit working directory.

The nmap adapter owns nmap-specific timing options.
The orchestrator still requests the existing initial fast scan when state is missing.
Keep the normal scan's current behavior.

Merge returns the complete next scan and the ordered changed-host collection.
It does not publish, write files, read settings, or invoke change callbacks.
Preserve pairing by MAC, IP fallback, grace periods, restart-floor behavior,
field retention, sorting, and the definition of a changed host.

The JSON state store owns loading, decoding, file naming, and atomic replacement.
Preserve the filename, persisted schema, working-directory default, and compatibility with old files.
Preserve logging and the existing fallback for missing or undecodable state.
Do not turn file-read failures into missing state when they currently propagate.
Keep interrupted saves and other save failures observable through the existing logging policy.

One scan cycle has an explicit order:

1. Load previous state, or perform the initial scan.
2. Perform the normal scan and enrich its hosts.
3. Compute the merged scan and changed hosts without side effects.
4. Publish changed-host events in their existing order.
5. Publish the complete scan event.
6. Save the merged scan.

The worker then pauses as it does today.
Publication remains synchronous.
Preserve current publish-failure behavior; do not introduce retries, queues, or rollback.
A successful merge does not imply successful publication or persistence.
Those outcomes remain distinct and logged.

## Scanner resource lifetime

One network session owns its scanner, mDNS instance/cache, SSDP cache/listener, and router table.
The application owns shared resources, including the publisher and bounded probe.
The worker manager owns starting, processing, and stopping sessions.
Remove the five parallel resource maps.

Keep current slice selection and scheduling behavior.
Do not change state/topic identity from interface address to network address in this refactoring.
A disappeared slice closes its session before a replacement is started.
A returning slice gets fresh caches and a fresh restart floor.

Acquisition and cleanup have explicit ownership.
A failure during startup closes resources already acquired for that session.
Closing a session stops all owned resources, even when one close operation fails.
Cleanup failures are reported; they are not silently swallowed.
Repeated close requests do not repeat resource teardown.
Preserve worker-failure propagation to the application.

The application owns shutdown registration.
The JmDNS factory no longer registers an independent hook per instance.
Closed sessions must not remain reachable through shutdown hooks.
Application-owned resources close after session workers stop.

## Display state and lifetime

Broker code owns connection callbacks, subscriptions, shared MQTT messages,
scan decoding, and metrics-topic filtering.
Keep one connection for scan and metrics consumers.
Keep QoS, topics, malformed-payload logging, and fresh-scan replacement behavior.

Place scan state beside network rendering and metrics state beside metrics presentation.
Put console state and clock helpers under display support.
Keep grouping, host formatting, icon selection, and CSS behavior within display ownership.
Split files by responsibility, not by a rule requiring one file per declaration.

Each app instance has one owned coroutine lifetime and an explicit disposal operation.
Feature stores, clock subscriptions, broker collection, and rendering work belong to that lifetime.
Do not create detached jobs or scopes inside those features.
Clock instances needed across features are owned and supplied by app composition.

Disposal ends the broker connection, subscriptions, ticking jobs, and owned render work.
Console interception must be released by its owner.
A subsequent app instance does not receive updates from its predecessor.
Preserve tick intervals, stale-data thresholds, and visible startup behavior.
Do not change the meaning of the startup-success callback as part of this work.

## Developer tooling and tests

Use explicit Python packages instead of adding the entire tests directory to the import path.
Developer commands live under `tools/netmon_dev`.
Group preview, benchmark, and asset-generation helpers beside their commands.
Move soak, apt-probe, and VM-device support there when shared by commands and tests.
Tests import these helpers through the package, not through bare sibling names.
Update uv's import configuration and Make commands together.

Organize tests by boundary:

| Location                              | Responsibility                                                                |
|---------------------------------------|-------------------------------------------------------------------------------|
| Kotlin common/JVM/JS test source sets | Contract, scanner rules/adapters, display state and DOM behavior              |
| `tests/unit`                          | Python tooling behavior and static checks                                     |
| `tests/browser`                       | Built production-page geometry, styling and asset checks in Playwright WebKit |
| `tests/system`                        | Booted scanner/display behavior, soak and apt probes                          |
| Existing package-local tests          | Installed Debian package behavior                                             |

Keep browser-only support under `tests/browser`.
The current `layout.py` contains support; `test_layout.py` contains the tests.
Keep the scripted WebSocket broker and geometry checks at that artifact boundary.
Do not move these checks into the current Chrome-based `jsTest` runner.
Playwright WebKit exercises the kiosk's engine family, not the exact deployed WPE build.
Installed kiosk checks remain separate.

Shared scan fixtures used by preview commands and browser tests have one tooling-owned definition.
Asset generators are not test fixtures.
Keep the Go metrics project where it is.
Retain its existing cross-language fixture use; do not migrate unrelated OTLP interfaces.

Preserve existing Make command names and pytest marker names.
Update paths, imports, fixture scope, collection hooks, and marker selection together.
Replace filename-based browser requirements in the root collection hook with explicit boundary selection.
Non-browser selections must not require installed browser binaries.

`make test` includes `test-layout`.
`make test-all` includes that coverage through `test`.
CI and release validation both run the layout suite against the built production bundle.
Keep browser installation explicit through `make browser`.
Do not make unrelated unit-test targets build the native scanner or boot a VM.

## Maintainer documentation

Create four current documentation entry points:

| Document                | Content                                                                                            |
|-------------------------|----------------------------------------------------------------------------------------------------|
| `docs/architecture.md`  | Component/dependency map, source ownership, startup and shutdown, ordered runtime flows, test map  |
| `docs/scanner.md`       | Discovery, enrichment, identity rules, merge, state, threading and scanner extension points        |
| `docs/display.md`       | Broker consumption, state, rendering, layout, clocks, assets and display extension points          |
| `docs/mqtt-contract.md` | Scan/host/metrics topics, payloads, optional fields, version compatibility, delivery and retention |

Move current material rather than copying it into multiple owners.
Keep `how-it-works.md` as a short navigation page so existing links have a landing page.
Update README, open-issue references, and active tooling instructions.
Mark historical specs/plans as historical where linked; do not rewrite their original designs.

The architecture page answers where a behavior lives and when it executes.
Each component page links to its composition entry point and its relevant test commands.
Cross-component documentation describes contracts, not the other component's internal implementation.

## Verification and acceptance

The implementation is accepted only when these observable requirements hold:

| Requirement                        | Evidence                                                                                                                                                       |
|------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Ownership matches the package map  | Source/dependency checks show no scanner-to-display imports, no contract-to-component imports, and no support-to-application-policy back-edges.                |
| Icons cannot change classification | Every shipped model retains its baseline recognition/kind; changing presentation symbols does not change classifier results.                                   |
| Merge has no I/O or callbacks      | Pure rule tests cover pairing, transitions, retained fields, restart floors, and the ordered changed-host collection.                                          |
| Scan wiring preserves behavior     | An orchestrator test with fake scan/store/publisher operations records the exact cycle order and current failure outcomes without nmap or live network access. |
| Old state still works              | Existing-format files load, new writes retain the schema/naming, and atomic replacement tests use a temporary directory.                                       |
| Sessions own resources             | Tests cover partial startup, disappearance/reappearance, repeated close, cleanup failure, and one teardown per acquired resource.                              |
| Shutdown ownership is bounded      | Tests show removed sessions leave no per-instance shutdown registration; application resources close after workers.                                            |
| Display lifetime is explicit       | DOM/flow tests cover two sequential app instances, disposal, broker closure, and stopped updates without wall-clock sleeps.                                    |
| Display output is preserved        | Existing DOM tests and the production-bundle WebKit geometry/style/asset suite retain their assertions and pass.                                               |
| Tooling remains usable             | Tests cover Python imports, Make command routing, marker selection, and collection without WebKit for non-browser tests.                                       |
| Aggregate validation is accurate   | Make dependency and workflow checks confirm layout coverage in `test`, `test-all`, CI, and release.                                                            |
| Runtime artifacts still launch     | Scanner entry points/native metadata and display distribution paths are validated by their existing build and package checks.                                  |
| Documentation is navigable         | Current pages have valid local links, clear owners, runtime-order descriptions, and correct test commands.                                                     |

Add focused tests before changing the corresponding behavior.
Reuse existing runners and fixtures.
Run targeted checks per extraction, then the affected JVM, JS, browser, Python, Go-fixture,
and package validation at integration.
The final implementation plan names the exact commands and selectors.
Do not replace artifact or installed-system checks with unit-test proxies.

## Migration constraints and exclusions

Each migration stage leaves a runnable scanner and display.
Package relocation, responsibility extraction, and behavior changes remain distinguishable.
Preserve serialized field names and tokens explicitly when moving declarations.
Preserve public Make targets and installed paths even when internal entry-point names move.

Do not change broker defaults, publish transport selection, retention semantics, host expiry,
identity trust order, device heuristics, discovery cadence, or state/topic network keys.
Do not introduce asynchronous publication, provenance tracking, or state-schema versioning.
Do not redesign the page, replace Fritz2/MQTT clients, or change metrics semantics.
Existing unrelated issues remain outside scope.

After ownership and dependency checks pass, a later spec may split Gradle modules.
That split is not an acceptance criterion for this refactoring.
