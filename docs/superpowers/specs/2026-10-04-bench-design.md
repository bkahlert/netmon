# A benchmark of the display on the board

## Intent

Compare two builds of the display on a real Pi, the same scripted scans for each, and see which costs less and settles
sooner. An A/B check for edits such as [28a4cf3](https://github.com/bkahlert/netmon/commit/28a4cf34f0f84637de49a0425e8a46fce1b00fe4),
not an absolute footprint and not a CI gate: one run by default for a quick look, more runs for a figure with its noise.

## Today

- The [footprint](2026-10-02-footprint-design.md) and [page load](2026-10-03-page-load-design.md) figures came from soaks
  and from a feeder publishing 53 hosts every 37 s, read by hand in windows after a settling time. Each comparison was set
  up anew.
- `make preview-board` points the board's kiosk at a page served from the workstation through one ssh tunnel and a drop-in under
  `/run` ([pihero-testkit's `board.Session`][board]); the page is the development bundle, whose cost differs from what
  `make deploy` installs.
- [netmon-metrics](2026-10-04-metrics-design.md) publishes the board's figures every 5 s as OTLP/JSON on
  `dt/netmon/<node>/metrics`; [sampling.py](../../../tests/sampling.py)'s `subscribed(target)` streams them as `Sample`s
  over a tunnel, and `UnitSample.cpu_seconds` carries each unit's `systemd.unit.cpu.time`.

[board]: https://github.com/bkahlert/pihero/tree/main/testkit

## Decisions

Settled with the user on 2026-10-04:

1. **A/B regression.** The figures compare variants with each other, run interleaved on one board in one sitting.
2. **Cost per phase and settle time**, both from netmon-metrics. Nothing in the page changes for the benchmark.
3. **Bundles served from the workstation.** Each variant is a production bundle (`jsBrowserDistribution`) the workstation builds and
   serves through `board.Session`'s tunnel. Nothing persistent changes on the board.
4. **The representative scenario** below: 53 hosts, then 8 changes 60 s later, then 90 s more.
5. **The board's scanner stops** for the benchmark, so nmap does not compete with the kiosk; cleanup starts it again.
6. **A standalone script**, [tests/bench.py](../../../tests/bench.py) behind `make bench`, not a pytest test and not a
   testkit flavor: a benchmark has no pass or fail, and the scenario and figures are netmon's own.
7. **One run per variant by default**; `RUNS` sets more.

## Design

### The command

```shell
make bench TARGET=pi@netmon.local VARIANTS="main ." RUNS=3
```

| Variable   | Default | Meaning                                                                        |
|------------|---------|--------------------------------------------------------------------------------|
| `TARGET`   |         | `user@host[:port]` of the Pi, required, as for `preview-board`                 |
| `VARIANTS` | `.`     | Space-separated git refs; `.` is the working tree. The first is the reference  |
| `RUNS`     | `1`     | Runs per variant, at least 1                                                   |

Every ref is resolved to its commit before anything is built; an unknown ref ends the command. A variant's label is the
ref as given with its short sha (`main a33715a`), the working tree's `. f5320cb+dirty` when it has changes.

### Bundles

A ref is checked out with `git worktree add --detach dist/bench/src/<sha> <sha>`, built there with `./gradlew
jsBrowserDistribution`, its `build/dist/js/productionExecutable/` copied to `dist/bench/bundles/<sha>/` and the worktree
removed. A bundle that exists is reused. The working tree is built in place every time, into
`dist/bench/bundles/working-tree/`; Gradle allows one build per project directory, so no preview may run meanwhile. All
builds finish before the board is touched.

One static HTTP server on a free port of the workstation serves `dist/bench/bundles/`, so a variant's page is
`/<directory>/index.html`. The page's assets are relative to it ([index.html](../../../src/jsMain/resources/index.html)).

### Once per benchmark

1. The board answers over ssh, has `pihero-kiosk` (`board.Session.check_kiosk`) and an active `netmon-metrics.service`.
   The workstation's port 8080 is free for the fake broker.
2. The fake broker starts: [preview_broker.py](../../../tests/preview_broker.py)'s container, with no fixture published.
3. The static server starts.
4. `board.Session` with the name `netmon-bench` opens one tunnel with reverse forwards for the static server and the
   broker (`board.forwards`).
5. `sampling.subscribed(target)` streams the board's metrics.
6. `sudo systemctl stop netmon-scanner.service`. The terminal says once that a killed benchmark leaves the scanner
   stopped until the next reboot.

### One run

Runs go interleaved, variant after variant: with `VARIANTS="main ."` and `RUNS=3` the order is `main . main . main .`.

1. The scan topic `dt/netmon/node/wlan0/10.0.0.1/24/scan` is cleared with an empty retained message, so the new page
   finds no scan of the run before.
2. `board.Session.install` writes the session conf with the variant's page URL and the broker's address as the board
   reaches them, restarts the kiosk and waits until its journal shows the page loaded. The restart gives every run a new
   cgroup and a new web process.
3. Scan 1 is published, retained, right after the first metrics sample after the load is seen. That sample's time is
   **t0**.
4. Scan 2 is published, retained, right after the first sample at or after t0 + 60 s.
5. The run ends at the first sample at or after t0 + 150 s.

The phases:

| Phase  | From                     | To                       |
|--------|--------------------------|--------------------------|
| load   | the kiosk's restart      | the sample at t0         |
| scan 1 | the sample at t0         | the sample before scan 2 |
| scan 2 | the sample before scan 2 | the run's last sample    |

Tying each publish to a sample makes every boundary exact to a sample. The load phase needs no first boundary: the
counters of the new cgroup and process start at zero.

### The scenario

Built from [scan_fixtures.py](../../../tests/scan_fixtures.py), every timestamp is t0 plus an offset, so every run sees the
same ages and the same highlight windows, and the same t0 gives the same payloads byte for byte. The scenario is a list
of `(offset, scan)` steps in `bench.py`; another scenario is another list.

- **Scan 1, offset 0:** the `14+39` fixture of source 0, 53 hosts. Its 14 recent hosts changed 30 to 43 s before t0, so
  their 60 s highlight ends during the phase.
- **Scan 2, offset 60 s:** the same hosts with 8 changes, their `since` the scan's time: 3 hosts up to down, 3 down to up,
  1 new host, 1 gone. The changes span the recent and the stable hosts, so every partition move happens. Eight is the
  count of highlighted hosts 28a4cf3 measured against.
- **End, offset 150 s:** the radar pulse ends at 70 s, the highlights at 120 s; the last 30 s are the idle baseline.

The display drops a scan older than 5 minutes (`ScanEventSettings.outdatedThreshold`); a run is 150 s.

### Figures

Per phase and run, from the samples at the phase's boundaries:

| Figure         | Source                                                                                    |
|----------------|-------------------------------------------------------------------------------------------|
| web CPU s      | delta of `SystemSample.web_cpu_seconds`, `process.cpu.time` of `WPEWebProcess`            |
| kiosk CPU s    | delta of `UnitSample.cpu_seconds` of `pihero-kiosk.service`: cog, web and network process |
| kiosk RAM+zram | `current + swap_current` of the kiosk at the phase's end, and the peak of its samples     |
| web anon       | `SystemSample.web_anon` at the phase's end                                                |
| major faults   | delta of `SystemSample.pgmajfault`                                                        |
| settle         | scan phases only, below                                                                   |

The peak is the phase's own, from its samples: the cgroup's `memory.peak` counts since the unit's start.

**Settle time.** The utilization between two samples is the web CPU delta over the elapsed time, in % of one core, as
the footprint figures are. The run's baseline is the mean utilization of the sample pairs from t0 + 130 s to the run's
end. A scan phase's settle time runs from its start to the first sample from which the utilization over the next three
intervals (15 s) is at most 10 points above the baseline. A phase that ends first reads `> 60 s` or `> 90 s`. The
resolution is the 5 s of the samples.

The idle page's single intervals swing by more than 10 points around the baseline: 28a4cf3's last 20 s read 6, 21, 5
and 22 % against a baseline of 17 %. A rule over single intervals within 2 points never settles on that. Settle time is
measured against each variant's own idle: a variant that is busy all the time settles at once, and its CPU figures show
its cost.

### A run that does not count

A run fails, with its reason, when between t0 and its end:

- the web process's PID changes,
- the kiosk's restarts or OOM kills grow,
- the boot id changes,
- no sample arrives for three intervals (`sampling.Samples.next` raises),
- or the page does not load within `board.Session.install`'s 90 s.

A failed run keeps its payloads and its row in the per-run table, and counts toward no median. The benchmark goes on
with the next run unless the board stopped answering, which ends it.

### Cleanup

On every exit, Ctrl-C and failures included, in this order: `board.Session.restore` (the kiosk shows the board's own
page again), `systemctl start netmon-scanner.service`, the tunnel closed, the static server and the container stopped.
A restore or start that fails prints its message; a reboot removes the session's files and starts the scanner.

### The report

`dist/bench/<YYYY-MM-DD-HHMM>/`:

- **`report.md`**
  - The header: target, date, boot id, every variant's label, runs per variant, the run order, "scanner stopped".
  - The summary: a row per phase and figure, a column per variant, and per further variant a Δ column against the first.
    With one run a cell is the value; with more, `median (min–max)` of the valid runs and `n/m valid`. A Δ whose ranges
    overlap is prefixed `~`. A Δ is in % for the CPU, memory and fault figures and in seconds for the settle time.
    Example:

    | phase  | figure    | main a33715a     | . f5320cb        | Δ .    |
    |--------|-----------|------------------|------------------|--------|
    | scan 2 | web CPU s | 31.2 (30.4–32.0) | 24.8 (24.1–25.5) | −20 %  |
    | scan 2 | settle    | 40 s (35–45)     | 20 s (20–25)     | −20 s  |

  - The per-run table: every run's figures in the order they ran, failed runs with their reason, so drift shows.
- **`runs/<n>-<directory>.jsonl`**: the raw metrics payloads of each run, with t0 and the phase boundaries, so a changed
  rule or a new figure is computed again without the board. The directory is the bundle's: `working-tree` or the commit's
  sha.

The terminal prints a line per finished run (`run 2/6 main a33715a: scan 1 18.3 s web CPU, settle 25 s, …`) and the
path of the report at the end.

### What else changes

- The [Makefile](../../../Makefile) gains `bench` with its help line.
- The [README](../../../README.md) gains `make bench` under "Build and test the packages".

## Tests

Each behaviour at the lowest level that catches its defect.

- **[tests/test_bench.py](../../../tests/test_bench.py), tier 0**, pure logic, no board, container or sleep:
  - the scenario: 53 hosts; scan 2 differs by exactly 3 down, 3 up, 1 new and 1 gone; timestamps are t0 plus offsets;
    the same t0 gives the same payloads;
  - `VARIANTS` and `RUNS`: refs and `.`, `RUNS` defaulting to 1 and rejecting 0, the interleaved order;
  - the timeline against a fake sample stream and publisher: scan 2 on the first sample at or after t0 + 60 s, the end
    on the first at or after t0 + 150 s, silence raising;
  - the figures from built `Sample`s: the CPU deltas, the load phase as the counters at t0, the peak and end memory, the
    fault delta;
  - the settle time: the baseline window, settling on three intervals' utilization, not on one calm interval, `> 60 s`
    when it never does;
  - a failed run per reason: PID change, kiosk restart, OOM kill, boot id;
  - the report: values for one run, `median (min–max)` for more, `~` on overlapping ranges, failed runs excluded with
    `n/m valid`, the per-run table in run order.
- **[tests/test_makefile.py](../../../tests/test_makefile.py)**: the `bench` target and its help line.
- **`-m preview`, Podman**, next to [test_preview_broker.py](../../../tests/test_preview_broker.py): a cleared retained
  topic leaves nothing for a new subscriber; the static server answers a variant's `index.html` and its assets under the
  variant's directory.
- **Not automated:** building a ref in a worktree (Gradle) and the board end to end. The acceptance run is
  `make bench TARGET=pi@netmon.local VARIANTS="a33715a 28a4cf3"`: each phase has its figures, and 28a4cf3 is clearly
  ahead of its parent in scan 2's web CPU and settle time. Its report goes into a Numbers section here.

## Out of scope

- The page's own responsiveness (marks, long tasks): the user chose the metrics' figures.
- A CI gate or thresholds, and the VM: the board is the only place whose CPU is the panel's.
- Comparing two benchmarks of different days; the raw payloads keep it possible.
- Refs before #54 against refs after it: the older pages poll `stats.json`, which the static server answers with 404, so
  their cost differs by that. Within either side the comparison holds.
- The status bar's pills: the fake broker carries no metrics, so they stay empty for every variant.
