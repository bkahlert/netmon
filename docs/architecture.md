# Architecture and ownership

These pages answer two maintainer questions: where a behavior lives, and when it runs.
Use [scanner.md](scanner.md) for JVM-side scan behavior, [display.md](display.md) for the web UI,
[mqtt-contract.md](mqtt-contract.md) for the wire contract, and [open-issues.md](open-issues.md) for known gaps.

## Source ownership map

The current package map is logical ownership, not extra Gradle modules:

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

Composition entry points live in
[`src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app)
and
[`src/jsMain/kotlin/com/bkahlert/netmon/display/app`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app).
The main application classes are
[`com.bkahlert.netmon.scanner.app.Application`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt)
and
[`com.bkahlert.netmon.display.app.app`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app/app.kt).

## Startup and shutdown ownership

### Scanner side

- [`Application`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt) owns process-wide resources:
  the MQTT publisher, the bounded lockdownd probe, the shared LAN HTTP client, and shutdown registration.
- [`SlicedApplication`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplication.kt)
  owns slice discovery, worker start, worker processing, and worker stop.
- [`NetworkSession`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/NetworkSession.kt)
  owns per-slice resources: JmDNS, the service cache, SSDP listening, the router table, and the restart floor.
- A disappeared slice closes its session before a replacement starts.
  A returned slice gets fresh caches and a fresh restart floor.
- Application shutdown stops workers before application-owned resources close.
  Removed sessions must not stay reachable through shutdown hooks.

### Display side

- [`app`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app/app.kt)
  owns one display lifetime per mounted instance.
- That lifetime owns the broker connection, topic subscriptions, current-time stores, metric parsing,
  scan stores, and both mounted renders.
- [`DisplayApp`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app/DisplayApp.kt)
  exposes the disposal boundary.
  Disposal closes subscriptions, stops ticking jobs, and releases owned render work.
- A later display instance must not receive updates from an earlier one.

## Exact scan order

One scan cycle runs in this order:

1. [`SlicedApplication`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplication.kt)
   resolves eligible interface addresses every 5 seconds.
2. The application keeps one slice per network and prefers a wired interface when Wi-Fi and Ethernet
   reach the same LAN.
3. A slice session opens JmDNS, SSDP, and FRITZ!Box discovery resources, then builds
   [`NetmonScanner`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/scan/NetmonScanner.kt).
4. Each scan cycle loads its previous state. If no state exists, `NetmonScanner` performs an
   initial scan using nmap's `Insane` (`-T5`) timing template to seed the baseline.
5. It performs the normal scan using the `Aggressive` (`-T4`) timing template, then enriches each host with
   clues in this exact order: router, mDNS, SSDP, OUI, then lockdownd only when no model clue exists,
   then DNS, then name tokens, then placeholder cleanup. The mDNS service-name enricher runs after
   identity enrichment.
6. `ScanResult` merges the normal scan with the previous state and derives the ordered changed-host
   collection.
7. `ScannerEventPublisher` publishes retained host events for changed hosts, then publishes the
   retained scan event.
8. The merged scan is persisted as the next state file. The worker sleeps for
   `SCANNER_PAUSE_DURATION` and repeats.

[`NmapScanAdapter`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/nmap/NmapScanAdapter.kt)
selects the timing template for each scan mode; [`NmapNetworkScanner`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/nmap/NmapNetworkScanner.kt)
builds and runs the corresponding `nmap -sn` command, with `-6` on IPv6 and `--datadir` when configured.
The scan loop waits on nmap, the bounded lockdownd probe, and MQTT acknowledgements only.
Discovery caches own their own network reads.

## Display flow

One display instance runs in this order:

1. [`main.kt`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app/main.kt)
   mounts the status and network targets and calls [`app`](../src/jsMain/kotlin/com/bkahlert/netmon/display/app/app.kt).
2. [`brokerMessages`](../src/jsMain/kotlin/com/bkahlert/netmon/display/broker/events.kt)
   opens one MQTT connection for scan and metrics topics.
3. [`scans`](../src/jsMain/kotlin/com/bkahlert/netmon/display/broker/events.kt)
   decodes `dt/netmon/+/+/+/+/scan` payloads into contract events.
4. [`metricsPayloads`](../src/jsMain/kotlin/com/bkahlert/netmon/display/broker/events.kt)
   keeps `dt/netmon/+/metrics` payloads separate from scan traffic.
5. [`ScanEventsStore`](../src/jsMain/kotlin/com/bkahlert/netmon/display/networks/ScanEventsStore.kt)
   replaces the current host list for each source and drops stale scans after
   `scan.outdatedThreshold`.
6. [`KioskStatsStore`](../src/jsMain/kotlin/com/bkahlert/netmon/display/metrics/KioskStatsStore.kt)
   decodes OTLP/JSON kiosk samples.
7. [`status`](../src/jsMain/kotlin/com/bkahlert/netmon/display/support/status.kt)
   renders console output, kiosk CPU, web CPU, memory, and startup age.
8. [`networks`](../src/jsMain/kotlin/com/bkahlert/netmon/display/networks/network.kt)
   groups hosts, resolves icons, formats labels, and renders cards.
9. Disposing the app stops both stores, closes the connection, and releases both renders.

## Metrics boundary

Metrics are display input, not scanner behavior.
The scanner publishes scan and host events only.
The optional `netmon-metrics` sampler publishes OTLP/JSON to `dt/netmon/<node>/metrics`, and the display subscribes
through the wildcard topic `dt/netmon/+/metrics`.
Only the display metrics package decodes that payload:
[`src/jsMain/kotlin/com/bkahlert/netmon/display/metrics`](../src/jsMain/kotlin/com/bkahlert/netmon/display/metrics).
Network cards do not inspect metric payloads.
Status rendering does not inspect scan internals beyond the stores it receives.

## Test boundaries

| Boundary | Owns | Primary paths | Command |
|---|---|---|---|
| Scanner JVM behavior | scan orchestration, discovery adapters, identity rules, merge/state rules | [`src/jvmTest/kotlin/com/bkahlert/netmon/scanner`](../src/jvmTest/kotlin/com/bkahlert/netmon/scanner) | `make test-jvm` |
| Display JS behavior | broker routing, stores, grouping, rendering helpers, presentation assets | [`src/jsTest/kotlin/com/bkahlert/netmon/display`](../src/jsTest/kotlin/com/bkahlert/netmon/display) | `make test-js` |
| Production-bundle layout | built page geometry, styles, loading animation, WebKit rendering | [`tests/browser`](../tests/browser) | `make test-layout` |
| Python tooling | make routing, collection hooks, docs/static checks, helper packages | [`tests/unit`](../tests/unit) | `uv run --frozen pytest tests/unit -m tier0 -q` |
| Installed/package behavior | systemd install, preview/device tooling, VM boot, board workflows | [`tests/system`](../tests/system) | `make test-tier1` and `make test-tier2` |

`make test` runs `make test-jvm`, `make test-js`, `make test-layout`, `make test-metrics`, `make test-tier0`, and
`make test-tier1`.
`make test-all` adds `make test-tier2`.
