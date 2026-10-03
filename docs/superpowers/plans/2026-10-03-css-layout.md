# The display's layout without JavaScript Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The browser fits the host cards to the viewport; no script measures or zooms.

**Architecture:** Kotlin renders the cards and writes the section counts and text lengths as custom properties. CSS in `styles.css` computes columns, rows, heights and sizes with container units and math functions.

**Tech Stack:** Kotlin/JS with fritz2, Tailwind 3 plus plain CSS, Karma for Kotlin tests, Playwright's WebKit for layout tests.

**Spec:** [2026-10-03-css-layout-design.md](../specs/2026-10-03-css-layout-design.md)

## Global Constraints

- Natural cell: 11.5625 rem by 5.25 rem, text column 7.06 em, stable section `sigma` 0.75 and `opacity: .5`.
- No `zoom` property, no `data-zoomed` attribute, no `MutationObserver`, no `getBoundingClientRect` or `scrollHeight` reads in the display's code.
- The display test's selectors `.host[data-status="up"]` and `.font-mono` keep matching.
- Tailwind classes are complete static strings; layout lives in `styles.css` classes.
- Tests first, helpers last, no comments in tests.

## Review Focus

- A scan whose hosts all sit in one section: the other section has count 0, no height, no divider.
- A host without name, vendor and model: the placeholders render and carry a length.
- A host that moves between sections: the counts follow.
- Two or three scans side by side at 800 px: each fits its own area.
- A narrow screen (390 px): one column of scans, no horizontal scroll.

---

### Task 1: Counts and lengths in Kotlin

**Files:**
- Modify: `src/jsTest/kotlin/com/bkahlert/netmon/ui/NetworkKtTest.kt`
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/ui/network.kt`
- Delete: `src/jsMain/kotlin/com/bkahlert/netmon/ui/zoom.kt`, `src/jsMain/kotlin/com/bkahlert/netmon/fritz2/observers.kt`, `src/jsMain/kotlin/com/bkahlert/netmon/fritz2/events.kt`

**Interfaces:**
- Produces: `.scan__hosts` carries `--unstable` and `--stable`; `ul.hosts--unstable` and `ul.hosts--stable`; every fitted line is `span.fit` with `--len`.

- [ ] **Step 1: Write the failing tests** in `NetworkKtTest`: counts on the hosts area, counts follow a store update, `--len` on name, vendor, ip and model, and no `zoom` or `data-zoomed` anywhere.
- [ ] **Step 2: Run** `./gradlew --no-daemon --console=plain jsBrowserTest`; expect the new tests to fail.
- [ ] **Step 3: Implement** the structure of the spec in `network.kt`; delete the zoom helpers.
- [ ] **Step 4: Run** the same command; expect all green.
- [ ] **Step 5: Commit** `refactor(display): drop the zoom loops and write counts for css`.

### Task 2: CSS

**Files:**
- Modify: `src/jsMain/resources/styles.css`, `src/jsMain/resources/index.html`

- [ ] **Step 1: Write the layout test** (Task 3) first, so it fails against the old CSS.
- [ ] **Step 2: Move the host card to `styles.css`** classes in `em`; add the grid, height and fit rules of the spec.
- [ ] **Step 3: Lock the page** in `index.html`: `100dvh`, `.networks` as a shrinking flex item.
- [ ] **Step 4: Build** with `./gradlew --no-daemon --console=plain jsBrowserDistribution` and run the layout test.
- [ ] **Step 5: Commit** `perf(display): let css fit the host cards to the viewport`.

### Task 3: Layout test

**Files:**
- Create: `tests/test_layout.py`; Modify: `Makefile`, `pyproject.toml` (marker)

- [ ] **Step 1: Write** the test: a static server for `build/dist/js/productionExecutable`, Playwright's `route_web_socket` answering MQTT CONNECT and SUBSCRIBE and publishing a scan, assertions of the spec's Tests section.
- [ ] **Step 2: Run** `make test-layout` against the old build; expect failures on fit.
- [ ] **Step 3: Commit** `test(display): check the layout at three sizes and four host counts`.

### Task 4: Check in the kiosk

- [ ] **Step 1:** `make build`, then `make test-tier2`.
- [ ] **Step 2:** Feed a 53-host scan to the VM's broker and read `kiosk.png`.
- [ ] **Step 3:** Soak for five minutes with the old and new build; compare the web CPU.
- [ ] **Step 4: Commit** the numbers into the spec: `docs(spec): record the layout's measurements`.
