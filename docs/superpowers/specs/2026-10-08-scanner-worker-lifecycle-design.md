# Scanner worker lifecycle design

## Goal

Keep scanner behavior unchanged while consolidating per-network worker and
resource ownership. A slice's session should be created, processed, and closed
as one owned lifecycle rather than coordinating separate callbacks through a
caller-managed map.

## Current design

The scanner's main modules have distinct responsibilities:

- [Application](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt)
  composes process-wide resources and one scanning graph per network slice.
- [SlicedApplication](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplication.kt)
  reconciles active slices and runs a worker thread for each.
- [NetworkSession](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/NetworkSession.kt)
  owns resources scoped to one slice and delegates scans to `NetmonScanner`.
- [NetmonScanner](../../../src/jvmMain/kotlin/com/bkahlert/netmon/scanner/scan/NetmonScanner.kt)
  owns scan-cycle ordering: load or establish initial state, scan, enrich,
  merge, publish changed hosts and the scan, then save state.
- Discovery, identity, nmap, state, and MQTT modules each own their respective
  behavior and I/O.

The lifecycle seam is less cohesive. `SlicedApplication` currently receives
separate `start`, `process`, and `finalize` callbacks. `Application` stores
`NetworkSession` instances in a `ConcurrentHashMap` so those callbacks can
share the session. This spreads per-slice lifecycle state across the worker
manager, the application, and the session. The lifecycle test repeats the
same callback and map coordination in
[`SlicedApplicationTest`](../../../src/jvmTest/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplicationTest.kt).

The design does not call for splitting the scan pipeline or changing the
existing discovery, identity, persistence, and publication seams.

## Proposed interface and ownership

Have `SlicedApplication` create one worker object for each active slice. A
worker owns its repeated operation and cleanup:

```kotlin
interface SliceWorker : AutoCloseable {
    fun process()
}

SlicedApplication(
    slice = ::currentSlices,
    open = ::openSliceWorker,
)
```

`SlicedApplication` retains slice reconciliation, worker threads, and failure
tracking. It owns each successfully opened worker until that worker is closed.
`NetworkSession` implements `SliceWorker`: `process()` delegates to
`NetmonScanner.scan()`, and `close()` releases the session's resources.
`Application` supplies the worker factory and no longer stores sessions in a
separate map.

The worker factory runs in the slice's worker thread, matching the current
`start` callback's execution context. Partial resource acquisition remains
owned by `NetworkSession.open`; a failed factory call cleans up acquired
resources before propagating the startup failure.

## Lifecycle and behavior invariants

- At most one worker is active for a slice.
- A worker is opened once, processed repeatedly, and closed once after its
  processing stops.
- Removing a slice interrupts and joins its worker before closing its session.
  If the slice returns later, it receives fresh resources and a fresh restart
  floor.
- A processing failure still triggers cleanup and marks the slice failed. A
  cleanup failure also marks the slice failed; if processing already failed,
  cleanup failure remains suppressed on the original failure.
- Application shutdown still stops and joins all workers before closing the
  shared MQTT publisher.
- Network selection, wired-interface preference, scan interval, scan-cycle
  order, event topics, state format, and published payloads do not change.

## Alternatives and trade-offs

### Keep callbacks and the map

This avoids an interface change, but callers still coordinate three lifecycle
callbacks and a concurrent map. Session lifecycle changes remain spread across
the manager and composition root. Existing tests cover the manager but repeat
the caller's coordination.

### Use an owned worker factory (recommended)

The caller supplies one factory; the worker owns processing and cleanup. The
manager and worker implementation localize lifecycle changes. Manager tests
can assert open/process/close behavior, while `NetworkSession` tests verify
resource cleanup. This removes the map and callback protocol. The public
`SlicedApplication` constructor changes, though `Application` is the only
in-repository production caller.

### Replace the manager with app-specific scheduling

Application code owns scheduling, sessions, and cleanup together, so lifecycle
logic is local to this application. However, this replaces the existing
manager lifecycle test surface and adds scanner-specific concurrency and
shutdown logic. It avoids a generic worker interface but increases total code.

The worker factory is the smallest design that moves session lifecycle ownership
to the module that enforces the worker lifecycle. It reduces coordination
without changing the scan pipeline or introducing new adapters for hypothetical
variation.

## Errors and compatibility

Worker creation failures continue to fail the slice. The factory is responsible
for cleaning up resources acquired before it returns a worker. Once returned,
the manager closes the worker in the same cleanup path used for normal stop and
processing failure. When both processing and close fail, the processing
failure remains primary and the close failure is suppressed.

The new constructor is source-incompatible for direct `SlicedApplication`
callers. There is one production caller in this repository. If this class is
consumed as a published library, retain a compatibility overload; otherwise,
migrate the in-repository caller and tests directly.

## Verification

Update the `SlicedApplication` tests to cover worker creation, repeated
processing, close-on-removal, fresh worker creation after reappearance, cleanup
after processing failure, and combined processing/cleanup failure. Keep the
`NetworkSession` resource-order and partial-startup-cleanup tests. Keep the
application shutdown-order test and the `NetmonScanner` cycle-order tests
unchanged.

Acceptance requires those lifecycle tests to pass, with the session map
removed from `Application`; scanner event ordering and state behavior remain
unchanged.

## Scope exclusions

- No scan, identity, enrichment, merge, state, MQTT, or network-selection
  behavior changes.
- No restructuring of discovery or identity packages.
- No switch to coroutines or a new scheduling library.
- No compatibility shim unless external consumers of `SlicedApplication`
  exist.
