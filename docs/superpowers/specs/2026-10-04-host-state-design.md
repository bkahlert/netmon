# Stable host state

## Intent

A host whose state is stable keeps it. One missed scan, a sleeping Wi-Fi client or a scanner restart does not turn it
DOWN, wipe its name and model, or announce it as new. This is slice A of three from the scanner brainstorm of
2026-10-04; B (identity: MAC, names, Apple devices) and C (discovery: nmap flags, extra sources) get their own specs.

## Today

All of it is in [ScanResult.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScanResult.kt) (`merge`, `save`)
and [NetmonScanner.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/NetmonScanner.kt).

- **Flapping.** A host missing from one scan is DOWN at once, with `since` set to now. The scan runs every 30 s plus its
  own duration, and a Wi-Fi client in power save misses ARP now and then.
- **Restart.** `name`, `model`, `vendor` and `services` are taken from the fresh scan only. nmap supplies the vendor
  and a reverse-DNS name; the enrichers add the model, the services and any name only mDNS knows. Right after a start the
  mDNS cache is empty, so the first scan lacks those: `merge` replaces the recorded values with `null`, calls
  `onChange` for every host whose merged record differs, and
  [Application.kt](../../../src/jvmMain/kotlin/com/bkahlert/netmon/Application.kt) publishes each as a host `up` event.
  A host the first scan misses goes DOWN as well.
- **Persistence.** The state file `scan.<interface>.<cidr>.json` lives in the working directory, which the unit sets to
  `/var/lib/netmon` (`StateDirectory`), so it survives restarts. `save` creates its temp file in the unit's
  `PrivateTmp`, not next to the target; a rename is atomic only within one filesystem, so a failed move can leave the
  old file replaced by a copy in progress.
- **Reused scanner.** `Application` keeps a `NetmonScanner` per address in `scanners`, but its `finalize` drops only the
  mDNS cache. When a network goes away and comes back, the same scanner returns with enrichers bound to the closed cache.

## Decisions

Settled with the user on 2026-10-04:

1. **Keep Kotlin and nmap.** Slices in the order A, B, C.
2. **Grace of 3 minutes.** A host turns DOWN after it was unseen for 3 minutes; UP is immediate. The value is a setting.
3. **Events only for status changes.** A host event fires for a new host or an UP/DOWN transition, no longer for any
   changed field.

## Design

### Host

`Host` gains `lastSeen: Instant?`, serialized as epoch seconds like `since`, default `null`. It is the time of the last
scan that saw the host UP. State files without it still load; the display ignores the field (the JSON format ignores
unknown keys), and an older scanner reading a new file ignores it too.

### Merge

`merge` is still a pure function of the recorded scan, the current scan and two inputs it gains: `downAfter` and
`notBefore`. A host is **seen** when the current scan has it with status UP or with no status at all (the old code treats
a missing status as UP too). A scanned host with any other status, DOWN or UNKNOWN, counts as unseen. A recorded status
other than UP (DOWN, UNKNOWN or none) counts as **not UP**.

| Recorded | Current scan | Result |
|---|---|---|
| none | seen | UP, `since = lastSeen = scan time`, fresh fields |
| none | scanned, unseen | DOWN, `since = scan time`, no `lastSeen`, fresh fields (as today) |
| UP | seen | UP, `since` kept (scan time if it has none), `lastSeen = scan time` |
| not UP | seen | UP, `since = lastSeen = scan time` |
| UP | unseen, `scan time − max(lastSeen, notBefore) < downAfter` | unchanged: still UP, `lastSeen` kept |
| UP | unseen, grace over | DOWN, `since` = recorded `lastSeen`, fields kept |
| DOWN | unseen | unchanged |
| none or UNKNOWN status | unseen | DOWN, `since = scan time`, fields kept |

The row "UP, seen" differs from today in one place: hosts of a first scan carry no `since` (the initial scan sets none,
and today a host that stays UP never gets one). They get the scan time on the next scan.

- **Fields.** For a seen host every field is the fresh value if there is one, otherwise the recorded value. An empty
  mDNS cache no longer erases anything; a real rename still wins. An unseen host keeps all recorded fields.
- **Missing `lastSeen`.** An old state file has none; the previous scan's timestamp stands in for it.
- **Restart floor.** `NetmonScanner` captures its construction time as `notBefore`, so the grace period of every UP host
  runs from the restart at the earliest. A scanner that was down for ten minutes does not flip the hosts its first scan
  happens to miss. The cost is that a device that left while the scanner was down shows UP for up to `downAfter` after
  the start. `since` of a late DOWN still uses the recorded `lastSeen`, not the floor.
- **Returning networks.** `Application`'s `finalize` also removes the network's `NetmonScanner`, so a network that comes
  back gets a new scanner: a fresh floor, and enrichers bound to the new mDNS cache instead of the closed one.
- **Setting.** `ScannerSettings.downAfter`, default 3 minutes, next to `pauseDuration`.

### Events

`onChange` fires for a host without a recorded counterpart and for a changed status. Enrichment changes and `lastSeen`
updates fire nothing; they reach the display with the next scan event. `Application` keeps mapping DOWN to a `down`
event and everything else to `up`.

### Persistence

`save` creates the temp file in the target's own directory (`file.toAbsolutePath().parent`) and moves it with
`ATOMIC_MOVE`, so a stop mid-write leaves the old file. A state file that fails to parse still falls back to the initial
scan and loses its history; with atomic writes that no longer happens through the scanner's own doing.

## Testing

[ScanResultTest.kt](../../../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/ScanResultTest.kt) drives `merge` with
explicit timestamps and no real clock.

**Existing tests to rewrite.** These encode the old semantics: `ip` (`since`, `changed`), `merge_name`, `merge_model`,
`merge_vendor` and `merge_services` (their `DOWN`/`DOWN` and `UP`/`DOWN` rows), `merge_since` and `merge_status`
(its UP to DOWN and none to DOWN rows).

**Harness.** `mergingShould` passes `downAfter` and `notBefore`. Its timestamps (100 and 200) are under the grace, so
no DOWN is reachable; the new cases choose their own. The `Host.Companion.invoke` helper in `HostTest.kt` gains
`lastSeen`.

**New cases**, each red before the change, one per row of the table plus:

- a host unseen for less than `downAfter` stays UP, `since` and `lastSeen` unchanged, no event
- unseen for `downAfter` or longer: DOWN, `since` = recorded `lastSeen`, one event; seen again: UP, `since` = scan time,
  one event
- a DOWN host that stays unseen: no change, no event
- an empty fresh scan keeps name, model, vendor and services of a seen host; a changed name wins
- an enrichment-only change fires no event; a seen UP host updates `lastSeen` without an event
- a recorded host without `lastSeen` uses the previous scan's timestamp
- `notBefore` later than `lastSeen` extends the grace period; earlier changes nothing
- scanned DOWN or UNKNOWN is unseen; a recorded none or UNKNOWN status becomes UP when seen, DOWN when not
- `lastSeen` survives a serialization round trip, and a state file without it loads

A `save` test checks that the state file is replaced in its own directory and that the old file is intact when writing
fails.

**Not unit-tested.** `NetmonScanner` passing `startedAt` and `ScannerSettings.downAfter` to `merge`, and `Application`
removing the scanner in `finalize`: `NmapNetworkScanner` needs the nmap binary. Both are one-line wirings, checked by
reading the diff.

QA: `./gradlew jvmTest --tests '*ScanResultTest'` passes, with the new cases red first. The JS tests and
[tests/scan_fixtures.py](../../../tests/scan_fixtures.py) need no change; the display decodes only scan events and the
JSON format ignores unknown keys.

## Out of scope

- **Identity by MAC (B).** A host that changes its DHCP address is still a new host, and the old address now needs
  `downAfter` instead of one scan to turn DOWN.
- **Liveness from mDNS or other sources (C).** Only nmap's result counts as "seen".
- **The initial `-T5` scan** when no state file exists stays as it is.
