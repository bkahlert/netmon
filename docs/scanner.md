# Scanner ownership and behavior

The scanner owns device discovery, identity resolution, merge rules, state persistence, and MQTT publication.
Its composition entry point is
[`src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt).
Its JVM tests live under
[`src/jvmTest/kotlin/com/bkahlert/netmon/scanner`](../src/jvmTest/kotlin/com/bkahlert/netmon/scanner).
Run `make test-jvm` for scanner behavior and `make test-tier0` for the Python-side static and package wiring around it.

## Ownership

The scanner packages are split by responsibility:

- [`scanner/app`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app): composition, settings, worker management,
  and network-session ownership.
- [`scanner/scan`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/scan): orchestration, merge rules,
  restart-floor handling, and scan settings.
- [`scanner/identity`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/identity): clues, trust order,
  placeholders, Apple model handling, and classifier tables.
- [`scanner/discovery`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery): mDNS, SSDP, FRITZ!Box,
  and lockdownd discovery adapters.
- [`scanner/nmap`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/nmap): command execution and XML parsing.
- [`scanner/state`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/state): JSON state-file persistence.
- [`scanner/mqtt`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/mqtt): retained scan and host publication.
- [`scanner/support`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/support): JVM-only helpers for cache,
  logging, network, process, and XML support.

## Discovery, enrichment, and merge

Each worker cycle runs in this order:

1. [`JsonScanStateStore`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/state/JsonScanStateStore.kt)
   loads the previous state. If none exists, [`NetmonScanner`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/scan/NetmonScanner.kt)
   runs an initial scan using `ScanMode.INITIAL` (`nmap -T5`) to seed the baseline.
2. [`NetmonScanner`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/scan/NetmonScanner.kt)
   runs the normal scan using `ScanMode.NORMAL` (`nmap -T4`). The
   [`NmapScanAdapter`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/nmap/NmapScanAdapter.kt)
   selects the timing template, and [`NmapNetworkScanner`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/nmap/NmapNetworkScanner.kt)
   collects raw hosts with `nmap -sn`.
3. [`IdentityEnricher`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/identity/IdentityEnricher.kt)
   collects clues from the router, mDNS, SSDP, and OUI, then lockdownd when no model exists,
   then DNS and name tokens, and applies the trust order per field.
4. [`HostServicesEnricher`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/scan/enrichment/HostServicesEnricher.kt)
   adds the mDNS service names announced at the host IP.
5. [`ScanResult`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/scan/ScanResult.kt)
   pairs hosts, derives UP and DOWN transitions, and keeps retained fields.
6. `NetmonScanner` publishes changed-host events in order, then publishes the complete scan event.
7. The merged scan is saved as the next state.

Merge and identity resolution are pure rule code.
They do not perform network or filesystem I/O.
Discovery caches own the network reads they need.

## Identity rules that matter

`IdentityResolver` fills each field from the first clue in that field's trust order:

| Field | Order, best first |
|---|---|
| `name` | `USER` > `PROTOCOL` > `MDNS_HOST` > `DNS` > `ROUTER` |
| `model` | `PROTOCOL` > `APPLE_CODE` |
| `vendor` | `NAME_TOKEN` > `OUI` > `PROTOCOL` > `APPLE_CODE` |
| `kind` | `USER` > `APPLE_CODE` > `PROTOCOL` > `NAME_TOKEN` > `ROUTER` > `OUI` > `Generic` |
| `link`, `speed` | `ROUTER` |
| `mac` | `ROUTER`, only when nmap reported none |

The clue order inside `IdentityEnricher` also matters.
mDNS models outrank SSDP models because mDNS clues are collected first inside the shared `PROTOCOL` source.
Accepted Apple codes come from
[`model-catalog.json`](../src/jvmMain/resources/assets/model-catalog.json).
New codes stay unclassified until someone assigns a kind.

## State and publication boundaries

State files live under the scanner working directory as:

```text
scan.<interface>.<address>_<prefix>.json
```

Each file stores the merged scan, not just the current UP hosts.
A new DHCP address starts a new state file.
The scan topic and host topic stay keyed by node, interface, and CIDR.
See [mqtt-contract.md](mqtt-contract.md) for the wire-level rules.

## Threading and resource ownership

One network session owns these per-slice resources:

- JmDNS and the mDNS service cache
- SSDP listening and the description cache
- the FRITZ!Box host table
- the scan restart floor

The application owns the shared MQTT publisher, the bounded lockdownd probe,
and the shared LAN HTTP client.
The scan loop waits on nmap, lockdownd, and MQTT acknowledgements only.

## Extending the scanner safely

### Add a name token

- Edit [`NameTokens.kt`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/identity/NameTokens.kt).
- Add matching coverage in
  [`NameTokensTest.kt`](../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/identity/NameTokensTest.kt)
  and, when needed,
  [`PlaceholdersTest.kt`](../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/identity/PlaceholdersTest.kt).
- Run `make test-jvm`.

### Add a clue source

- Put its network access under
  [`src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/discovery).
- Keep its resolver-facing API small, like `MdnsLookup`, `SsdpLookup`, or `RouterHostLookup`.
- Implement the clue source under
  [`src/jvmMain/kotlin/com/bkahlert/netmon/scanner/identity`](../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/identity).
- Cover the source with JVM tests under
  [`src/jvmTest/kotlin/com/bkahlert/netmon/scanner`](../src/jvmTest/kotlin/com/bkahlert/netmon/scanner).
- Run `make test-jvm` and `make test-tier0`.

### Add a new kind or change classification

- Update [`Kind.kt`](../src/commonMain/kotlin/com/bkahlert/netmon/contract/Kind.kt) and the classifier inputs that can emit it.
- Keep the wire token stable.
- Update the classifier tests in the scanner JVM suite.
- Update the display grouping and icon assets only after the scanner classification is correct.
  See [display.md](display.md) for the display-owned follow-up.
