# Scanner Worker Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate each active network slice's creation, repeated processing, and cleanup into one worker-owned lifecycle without changing scanner behavior.

**Architecture:** `SlicedApplication` will open one `SliceWorker` per active
slice and own it until processing stops. `NetworkSession` will implement that
interface, letting `Application` remove its session map and separate lifecycle
callbacks while leaving scan orchestration and resource ownership intact.

**Tech Stack:** Kotlin/JVM, Gradle, Kotest/JUnit 5.

**Spec:** [2026-10-08-scanner-worker-lifecycle-design.md](../specs/2026-10-08-scanner-worker-lifecycle-design.md)

## Global Constraints

- Preserve wired-interface preference, scan interval, scan-cycle order, event topics, state format, and published payloads.
- Stop and join each worker before closing its session; close shared MQTT resources only after workers stop.
- Preserve failure tracking and keep a processing failure primary when cleanup also fails.
- Do not restructure scan, identity, discovery, persistence, or MQTT modules.

## Review Focus

- A worker factory throws after partial acquisition: partial resources are
  released and the slice is failed. Cover with
  `NetworkSessionTest.scanner_creation_failure_remains_primary_when_cleanup_also_fails`.
- Processing fails after worker creation: close still runs exactly once and the slice is failed. Cover with a `SlicedApplicationTest` worker-failure test.
- Both processing and close fail: preserve the processing exception and
  suppress the close exception. Cover with a `SlicedApplicationTest`
  combined-failure test.
- A removed slice returns: the old worker is closed before a fresh worker is
  opened. Cover with the existing reappearance lifecycle test, rewritten
  against workers.
- Application shutdown races with active processing: worker/session cleanup
  precedes publisher close. Keep
  `ApplicationTest.shutdown_stops_workers_before_closing_the_publisher`.

---

### Task 1: Make slice workers own per-slice lifecycle

**Files:**
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplication.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/NetworkSession.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt`
- Modify: `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplicationTest.kt`
- Modify: `src/jvmTest/kotlin/com/bkahlert/netmon/scanner/app/NetworkSessionTest.kt`

**Interfaces:**
- Introduce `SliceWorker : AutoCloseable` in `SlicedApplication.kt` with `fun process()`.
- Replace separate `start`, `process`, and `finalize` callbacks with `open: (T) -> SliceWorker`.
- Make `NetworkSession` implement `SliceWorker`; its `process()` delegates to `NetmonScanner.scan()`, and `close()` continues to release owned resources.
- `Application` supplies the worker factory and no longer stores `NetworkSession` instances in a `ConcurrentHashMap`.

- [ ] **Step 1: Write worker lifecycle tests**

  Rewrite the `SlicedApplicationTest` lifecycle fixtures to open `SliceWorker`
  instances. Assert one open per active slice, repeated `process()` calls,
  and one close after termination. Update the slice-removal/reappearance test
  to assert the removed session closes before a fresh worker is opened.

  Add assertions that a processing failure closes the worker, a close failure
  marks the slice failed, and a close failure is suppressed when processing
  already failed. Keep coverage that factory failures mark the slice failed.

  Update `NetworkSessionTest` to call `process()` and retain coverage for
  idempotent close, reverse-order resource release, and cleanup when session
  construction fails.

- [ ] **Step 2: Run the focused tests and observe the expected failure**

  Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.scanner.app.SlicedApplicationTest' --tests 'com.bkahlert.netmon.scanner.app.NetworkSessionTest'`

  Expected: compilation or test failures identify the missing worker interface and the old callback-based lifecycle.

- [ ] **Step 3: Replace callback lifecycle with owned workers**

  In `SlicedApplication.kt`, add `SliceWorker` and change the constructor to
  accept `open: (T) -> SliceWorker`. Open the worker inside its slice thread,
  repeatedly call `process()`, and close it in the existing cleanup path after
  processing stops. Keep slice reconciliation, interrupt-and-join ordering,
  state transitions, and failure suppression behavior unchanged. Update the
  public KDoc to describe worker creation, processing, and closure guarantees.

- [ ] **Step 4: Make `NetworkSession` the worker**

  In `NetworkSession.kt`, implement `SliceWorker`; rename its scan delegation
  to `process()` and preserve `NetworkSession.open`'s partial-startup cleanup
  behavior.

- [ ] **Step 5: Remove session-map coordination from `Application`**

  In `Application.kt`, construct each `NetworkSession` from the worker factory,
  and remove the `ConcurrentHashMap` plus separate `start`, `process`, and
  `finalize` callbacks. Preserve the existing per-slice resource construction,
  topic selection, and application resource shutdown ordering.

- [ ] **Step 6: Run focused and scanner-wide verification**

  Run:

  ```shell
  ./gradlew jvmTest \
    --tests 'com.bkahlert.netmon.scanner.app.SlicedApplicationTest' \
    --tests 'com.bkahlert.netmon.scanner.app.NetworkSessionTest' \
    --tests 'com.bkahlert.netmon.scanner.app.ApplicationTest' \
    --tests 'com.bkahlert.netmon.scanner.scan.NetmonScannerTest'
  ```

  Expected: all selected tests pass, including manager failure/reappearance cases, shutdown ordering, and unchanged scan-cycle ordering.

  Run: `make test-jvm`

  Expected: the full scanner JVM suite passes.

- [ ] **Step 7: Commit the implementation**

  ```bash
  git add src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplication.kt \
    src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/NetworkSession.kt \
    src/jvmMain/kotlin/com/bkahlert/netmon/scanner/app/Application.kt \
    src/jvmTest/kotlin/com/bkahlert/netmon/scanner/app/SlicedApplicationTest.kt \
    src/jvmTest/kotlin/com/bkahlert/netmon/scanner/app/NetworkSessionTest.kt
  git commit -m "refactor(scanner): give slice workers lifecycle ownership"
  ```
