# Host identity and model

## Intent

A device is the same host when its IP changes, and a different host when another device takes its IP. The Apple devices
on the LAN show their model, and a host with a model or MAC is no longer a bare ❔. This is slice B of three from the
scanner brainstorm of 2026-10-04; A ([host state](2026-10-04-host-state-design.md)) is merged, C (discovery tuning) gets
its own spec.

Success:

- The iPads "Rabban" and "Feyd" show *iPad 6th generation* and *iPad Pro 11-inch*, read from the network.
- A host that moves to a new IP stays one host, with its recorded name, model and services.
- A new device on a recycled IP does not inherit the old host's fields.
- A model read once survives a restart (slice A already keeps recorded fields).

## Today

- [Host.kt](../../../src/commonMain/kotlin/com/bkahlert/netmon/Host.kt) has no MAC.
  [NmapXml.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/nmap/NmapXml.kt) reads only the MAC's `vendor`.
- [ScanResult.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt) `merge` pairs hosts by IP
  (`// TODO improve detection, e.g. by MAC address and/or hostname`). After A a recycled IP inside the 3-minute grace
  inherits the old host's recorded fields.
- nmap runs with `-sn`: a ping scan with no port data.
- Enrichers run on each fresh scan host, before `merge`, and cannot see the recorded state.
  [AppleHostEnricher.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/enrichment/AppleHostEnricher.kt) takes a model
  from an mDNS TXT value that is a key in
  [device-model-codes.json](../../../src/commonMain/resources/assets/device-model-codes.json). The iPads' only mDNS
  record (`_companion-link._tcp`) carries no model (`rpMd` is absent).
- iOS answers `GetValue ProductType` on TCP 62078 (`lockdownd`) without pairing. Probed on the LAN: Rabban `iPad7,5`,
  Feyd `iPad8,3`; both codes are in the table. The protocol is undocumented; a sleeping device may not answer.
- A device with a private (randomized) MAC has no vendor.
- The display consumes only `ScanEvent` (a full host list) and keys elements by IP, so it needs no change for a host
  that moves. Its caption falls back from name to model name to ❔.

## Decisions

Settled with the user on 2026-10-04:

1. **No manual override table.** The model comes from the network.
2. **The MAC follows the device.** The same MAC at a new IP is one host with the new IP.
3. **The probe caches itself.** The probe enricher keeps its own cache; the scanner pipeline (`scan`, `enrich`, `merge`)
   keeps its order.
4. **A superseded host is dropped.** Settled while planning: a recorded host whose IP a different device took leaves the
   list at once instead of turning DOWN, so IPs stay unique.

## Design

### Host

`Host` gains `mac: String?` (`@SerialName("mac")`), lowercase with colons. It is optional on the wire; both sides ignore
unknown keys, so scanner and display can differ in version. `HostPropertyEnricher.copy` learns the field.

`NmapXml` reads the `addr` of the `mac` address element next to its `vendor`. Unprivileged nmap reports no MAC; those
hosts keep the IP as their only identity, as today.

### Matching in `merge`

Two passes, each recorded host matched at most once:

1. **By MAC.** A scanned and a recorded host with equal MACs are the same host. The merged host takes the scanned IP.
2. **By IP.** Among the rest, a scanned and a recorded host with equal IPs are the same host when the recorded MAC or the
   scanned MAC is missing. This keeps old state files (no MACs) and unprivileged scans working; the host gains its MAC on
   the next privileged scan.

Different MACs at one IP are different hosts: the scanned one is new. A recorded host that no scanned host matched is
dropped from the list at once, without an event, when a scanned host that is up holds its IP. So no two hosts share an
IP, the display keeps keying cards by IP, and rotating private MACs leave no DOWN ghosts. The cost: a sleeping device whose
IP another device took vanishes instead of showing DOWN, and returns as a new host when seen. Randomized MACs get no
special case. The merged list stays sorted by IP. `onChange` keeps firing for a new host or a status change only; a pure
IP change sends no host event.

### Model probe

A new `LockdownModelEnricher`, in the chain after `AppleHostEnricher`.

- **Candidates.** Hosts without a model whose vendor is `null` or starts with "Apple". Private-MAC iPads have no vendor,
  so `null` qualifies.
- **Request.** TCP connect to 62078 (1 s timeout), send a 4-byte big-endian length and the XML plist
  `{Request: GetValue, Key: ProductType, Label: netmon}`, read the length-prefixed reply (2 s timeout). The reply is read
  with the `XMLStreamReader` setup of `NmapXml` (no DTD, no external entities). No new dependency.
- **Result.** `Value` becomes `model`; a host without a vendor gets "Apple Inc.". A model absent from the table is still
  stored.
- **Cache.** Keyed by MAC, by IP without one. A success lasts 24 hours, a failure 5 minutes. The clock is a constructor
  parameter. A restart probes each iPad once more.
- **Cost.** Probes run one after another in the scan thread. Hosts with a known vendor are never probed, and the failure
  cache bounds the rest to one 1 s connect per unknown host per 5 minutes.

### Names

No synthesized names. A probed iPad shows its model as caption. Whether `HostNameEnricher` already maps
`Rabban.local` to nmap's IPv4 is unverified: the first plan task dumps `JmDNSServiceInfoCache` for the iPads. If the
mapping is broken, fixing it comes first and may need no more. A Companion Link instance-name enricher is added only if
the dump shows `HostNameEnricher` cannot cover the name.

### Display

A host with neither name nor model shows the last three MAC octets (`a1:b2:c3`) as its caption instead of ❔; ❔ remains
without a MAC.

## Tests

Written first, in the style of the file edited.

- `NmapXmlTest`: the MAC is read; absent for a host without one.
- `ScanResultTest`: the IP moves with the MAC and keeps name, model and services; a recycled IP with a different MAC is
  a new host and the old one is dropped without an event; a recorded host unseen at an IP nobody took keeps the grace; one side without MAC matches by IP; an old state file gains the MAC; two
  hosts swapping IPs keep their fields.
- `LockdownModelEnricherTest`: a fake lockdownd on loopback answers `ProductType`; the cache hit skips the connect; a
  failure is retried after 5 minutes; a host with a non-Apple vendor is not probed; a closed port or timeout returns
  `null`. The port is a constructor parameter defaulting to 62078.
- Display: the MAC fallback caption.

QA: `make test-jvm` and `make test-js` pass. A real scan on the LAN then shows Rabban and Feyd with their models.

## Out of scope

- DOWN `since`: a late DOWN gets `since = lastSeen`, so the 60 s highlight never fires for DOWN. Undecided; separate.
- OS version, router and neighbour-table sources (C), a Go rewrite.
- Not yet verified: whether `FileCache.update` stops the scanner at startup when the MAC-prefix refresh fails after 30
  days offline. The plan checks `NmapMacPrefixesProvisioner` before any claim.
