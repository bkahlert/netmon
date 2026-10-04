# Metrics over MQTT as OTLP

## Intent

One sampler on the board measures the scanner, the kiosk and the system, and publishes the figures in the shape
monitoring tools speak: OpenTelemetry's metric names and its wire format, OTLP/JSON, over the broker the board already
runs. The panel's status bar and the soak read the same messages, so there is one source of truth instead of two
samplers with different figures.

## Today

- [netmon-display-stats](../../../packages/netmon-display/root/usr/lib/netmon/netmon-display-stats) is a bash loop in
  `netmon-display`. Every 5 s it writes three figures to `/run/netmon-display/stats.json`: the kiosk's CPU, the web
  process's CPU and the kiosk's RAM plus zram. lighttpd serves the file through a symlink, the page polls it
  ([stores.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/stores.kt), `KioskStatsStore`).
- [tests/sampling.py](../../../tests/sampling.py) is a second, richer sampler on the Mac. Over ssh it reads both units'
  memory, swap, peaks, restarts and OOM kills, the web and scanner processes' `smaps_rollup`, meminfo, vmstat, PSI, zram
  and `top`, for [the soak](../../../tests/test_soak.py) and [the apt probe](../../../tests/test_apt.py).
- The two share neither figures nor format.

## Decisions

Settled with the user on 2026-10-04:

1. **Both consumers.** The panel and the soak (with the apt probe) read the new messages. The board publishes
   everything `sampling.py` reads today, except `top`.
2. **OTLP/JSON.** One `ExportMetricsServiceRequest` per interval, the body an OTel collector accepts on `/v1/metrics`,
   with semantic-convention names where they exist.
3. **Go.** A static arm64 binary: the official OTLP proto types and `protojson` give spec-exact JSON, and its RSS of
   about 6 to 10 MB is affordable on the 415 MB board. Rust would save about 5 MB, no CPU worth measuring, and take longer.
   Bash would fork nothing but builds 30 metrics in `printf`; Python costs 15 to 20 MB; the OTel Collector 60 to 100 MB
   and has no MQTT exporter; folding the sampler into the scanner blinds it exactly when the scanner fails.
4. **A third package, `netmon-metrics`.** The apt probe reinstalls a package next to the live stack, and a package's
   postinst restarts its units: shipped in `netmon-scanner`, the sampler would restart during the very run that
   measures the reinstall.

## Design

### Package and unit

`packages/netmon-metrics/`, laid out as the two existing packages: `nfpm.yaml` (`arch: arm64`, depends on `mosquitto`),
`root/usr/lib/systemd/system/netmon-metrics.service`, `units.txt`, `scripts/`, `tests/test_installed.py`. The binary is
`/usr/lib/netmon/netmon-metrics`. [devices/sample/user-data](../../../devices/sample/user-data) installs the package
next to the other two.

The unit runs `DynamicUser=yes`, `Restart=always`, `Nice=10`, `MemoryMax=24M` with `GOMEMLIMIT=16MiB`, and keeps the
sandbox of `netmon-display-stats.service` with these changes: `PrivateNetwork=` goes; `RestrictAddressFamilies=AF_UNIX
AF_INET AF_INET6`; `IPAddressAllow=localhost` next to `IPAddressDeny=any`. AF_UNIX is for systemd's D-Bus API. The
command line names what it watches:

```
ExecStart=/usr/lib/netmon/netmon-metrics \
  --unit netmon-scanner.service --unit pihero-kiosk.service \
  --process WPEWebProcess --process netmon-scanner
```

Defaults: `--interval 5s`, `--broker tcp://localhost:1883`, `--root /` (the directory holding `proc/` and `sys/`, for
fixtures), `--node` the unqualified hostname.

### Source and build

Go source in `metrics/` with its own `go.mod`, the newest stable Go. Dependencies:

- `go.opentelemetry.io/proto/otlp` and `google.golang.org/protobuf/encoding/protojson` for the message,
- `go.opentelemetry.io/otel/semconv/v1.N.0` for the semconv names and `SchemaURL`, the newest `v1.N.0` package of that
  module when the work starts,
- `github.com/eclipse/paho.golang/autopaho` for MQTT,
- `github.com/coreos/go-systemd/v22/dbus` for the units' state and restarts.

`make metrics` runs `CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath` into `build/native/netmon-metrics`;
`make build` depends on it, and `make test-metrics` runs `go test ./...`, which `make test` includes. CI and the release
workflow gain `actions/setup-go`, pinned by commit hash as the other actions are.

### The loop

Every interval the sampler reads all sources of the catalogue, builds one request, marshals it with `protojson` and
publishes it to `dt/netmon/<node>/metrics`, retained, QoS 1. A Last Will and a clean shutdown on SIGTERM publish an
empty retained payload there, which clears the topic.

Processes are found in the watched units' `cgroup.procs` by `comm`; the first match per name counts.

### The message

One `ResourceMetrics` per entity, each with one `ScopeMetrics` named `netmon-metrics` at the build version, and the
request's `schemaUrl` the semconv package's `SchemaURL`.

| Entity  | Resource attributes                                                                     |
|---------|-----------------------------------------------------------------------------------------|
| host    | `host.name`, `host.boot.id` *, `service.name`=`netmon-metrics`, `service.version`         |
| unit    | `host.name`, `systemd.unit.name`                                                         |
| process | `host.name`, `systemd.unit.name` of its unit, `process.pid`, `process.executable.name`   |

Every data point carries `timeUnixNano`, the time of the sample. Counters are cumulative and monotonic, with
`startTimeUnixNano` the boot time (host), the unit's `ActiveEnterTimestamp` (unit) or the process's start from field 22
of `/proc/<pid>/stat` (process). A new start time is OTel's reset signal: consumers never take a delta across it.

`*.cpu.utilization` is the CPU time's delta to the previous sample of the same start time, divided by the elapsed
time and by `system.cpu.logical.count`, as the semantic conventions define it. A sample without such a predecessor has
no utilization point.

### The catalogue

`*` marks names without a semantic convention. They follow the OTel naming rules; `systemd.unit.state` and its
attribute match the collector-contrib systemd receiver.

| Entity  | Metric                                                              | Instrument      | Unit | Source                                       |
|---------|---------------------------------------------------------------------|-----------------|------|----------------------------------------------|
| host    | `system.cpu.logical.count`                                          | gauge           | 1    | `/sys/devices/system/cpu/online`             |
| host    | `system.memory.limit`                                               | gauge           | By   | meminfo MemTotal                             |
| host    | `system.memory.linux.available`                                     | gauge           | By   | meminfo MemAvailable                         |
| host    | `system.paging.usage` {`system.paging.state`=used, free}            | gauge           | By   | meminfo SwapTotal, SwapFree                  |
| host    | `system.paging.operations` {`system.paging.direction`=in, out}      | counter         | 1    | vmstat pswpin, pswpout                       |
| host    | `system.paging.faults` {`system.paging.fault.type`=major}           | counter         | 1    | vmstat pgmajfault                            |
| host    | `system.linux.cpu.load_1m` *                                        | gauge           | 1    | loadavg                                      |
| host    | `system.linux.memory.pressure.stall_time` * {kind=some, full}       | counter         | s    | `/proc/pressure/memory` `total`              |
| host    | `system.linux.zram.memory.usage` *                                  | gauge           | By   | `/sys/block/zram0/mm_stat` mem_used_total    |
| unit    | `systemd.unit.state` {`systemd.unit.active_state`}                  | gauge, 1 or 0   | 1    | D-Bus ActiveState                            |
| unit    | `systemd.unit.restarts` *                                           | counter         | 1    | D-Bus NRestarts                              |
| unit    | `systemd.unit.cpu.time` *                                           | counter         | s    | `cpu.stat` usage_usec                        |
| unit    | `systemd.unit.cpu.utilization` *                                    | gauge           | 1    | derived                                      |
| unit    | `systemd.unit.memory.usage` * {type=ram, swap, anon, file}          | gauge           | By   | `memory.current`, `memory.swap.current`, `memory.stat` |
| unit    | `systemd.unit.memory.peak` * {type=ram, swap}                       | gauge           | By   | `memory.peak`, `memory.swap.peak`            |
| unit    | `systemd.unit.memory.oom_kills` *                                   | counter         | 1    | `memory.events` oom_kill                     |
| process | `process.cpu.time` {`cpu.mode`=user, system}                        | counter         | s    | `/proc/<pid>/stat` utime, stime              |
| process | `process.cpu.utilization`                                           | gauge           | 1    | derived                                      |
| process | `process.memory.usage`                                              | gauge           | By   | `/proc/<pid>/status` VmRSS                   |
| process | `process.linux.memory.usage` * {type=anon, file, swap}              | gauge           | By   | `/proc/<pid>/status` RssAnon, RssFile, VmSwap |

`systemd.unit.state` publishes one point per active state, 1 for the current one, as the contrib receiver does.

The process figures come from `/proc/<pid>/status`, which every user may read. `smaps_rollup`, which the soak reads
today as root, needs `CAP_SYS_PTRACE` for another user's process, a capability the sampler will not hold. Its
`Private_Dirty` is replaced by `RssAnon`, which for the web process is the same memory within a few MB; the soak's
column and summary are renamed to say anon.

### Failures

- A source that does not open, a unit that is not loaded, a process that is gone: its points or its whole resource are
  left out, never published as zero or null.
- A counter below its previous value under the same start time yields no utilization point for that sample.
- The broker is down: autopaho reconnects; samples meanwhile are dropped, not queued. Sampling goes on, so the
  utilization deltas stay continuous.
- The sampler is dead: the Last Will clears the topic. After a reboot Mosquitto's persistence may still hold an old
  retained message, so every consumer drops a message whose `timeUnixNano` is three intervals or more from its clock.

### The panel

- [events.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/ui/events.kt)'s `mqttMessageFlow` subscribes to several
  topics on one client, so the page holds one websocket for the scans and the metrics.
- `KioskStatsStore` subscribes to `dt/netmon/+/metrics` instead of polling `stats.json`. An empty payload sets it to
  `null`; a message that does not decode logs once, as `scanFlow` does, and sets it to `null`.
- A `@Serializable` subset of OTLP/JSON (ignoring unknown keys) decodes the message into `KioskStats`, whose three
  figures keep their meaning: `kioskCpu` is `systemd.unit.cpu.utilization` of `pihero-kiosk.service` times
  `system.cpu.logical.count` times 100, a percentage of one core as the footprint numbers use; `webCpu` the same for
  `process.cpu.utilization` of `WPEWebProcess`; `kioskMemory` the unit's `ram` plus `swap` usage. `at` is the
  `timeUnixNano` in seconds, the interval stays the page's constant `KioskStats.INTERVAL`.
- [status.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/ui/status.kt) is unchanged.

### The soak and the apt probe

- The soak opens [booted.py](../../../tests/booted.py)'s ssh tunnel to the target's 8080 and subscribes to
  `dt/netmon/+/metrics` with `paho-mqtt` over websockets, a new dev dependency, recording every message for the duration.
- [sampling.py](../../../tests/sampling.py) decodes OTLP messages into its `Sample` dataclasses and keeps
  `render_table` and `render_summary`; its ssh readers go. The deltas (Δswpin, Δswpout, Δmajflt, Δweb cpu) come from
  the counters and are `n/a` across a new start time. PSI full10 becomes the share of the interval stalled, from the
  `full` stall-time delta. The `top` excerpt goes.
- `--soak-interval` is the spacing of the table's rows; the board samples every 5 s regardless.
- "The target stopped answering" becomes "no message for three intervals". The boot check compares `host.boot.id`.
- The apt probe reads its unit figures, peaks included, from the same stream.

## Testing

Each behaviour at the lowest level that catches its defect:

- **Go, `go test`.** The readers against a fake root, ported from
  [test_stats.py](../../../packages/netmon-display/tests/test_stats.py): counters, a web process restarted between two
  samples, a missing kiosk, a `comm` with spaces and parentheses. The builder's output round-trips through
  `protojson.Unmarshal` into `ExportMetricsServiceRequest` with the catalogue's names, units and temporality. The
  utilization on a first sample, a restart and a counter going backwards, with an injected clock and no sleeps.
- **Contract fixture.** A Go test writes a golden message to `metrics/testdata/metrics.json` and fails when the file
  is out of date, unless run with `-update`. The JS and Python decoder tests read that file, so no decoder drifts from
  the producer.
- **JS.** Decoding the golden into `KioskStats`, including the scaling by the core count and missing resources as
  `null`; the store's freshness and the empty payload; one client subscribing to both topics.
- **Python.** Decoding the golden into `Sample`; deltas across a restart; a boot id change; `render_table` and
  `render_summary` on decoded samples.
- **Tier 1, installed.** The package at the built version, the unit active, a retained message on the topic with the
  host and `netmon-scanner.service` resources, the topic empty after `systemctl stop`.
- **Tier 2, VM.** The `pihero-kiosk.service` and `WPEWebProcess` resources present; the panel shows the pills.

## Removed

`netmon-display-stats` and its unit, the `stats.json` symlink in
[netmon-display's nfpm.yaml](../../../packages/netmon-display/nfpm.yaml), the `stats.json` rule in
[lighttpd-netmon.conf](../../../packages/netmon-display/conf/lighttpd-netmon.conf), `test_stats.py`, the `stats.json`
cases of `netmon-display`'s `test_installed.py`, `loadKioskStats`, and every other mention the implementation turns up
(README, `test_boot.py`, `test_static.py`, `webpack.config.d/dev-server.js`).

## Out of scope

- A bridge from the topic to an OTel collector or Grafana. The message is the body `/v1/metrics` accepts, so it stays a
  few lines whenever it is wanted.
- Metrics in the preview: the fake broker publishes none, so the pills stay empty there, as today.
