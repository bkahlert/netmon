# Kiosk Stats in the Status Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The panel's status bar shows the kiosk's CPU and memory as three pills, so a UI change's cost on the board is visible on the board.

**Architecture:** A page cannot read its own process's CPU, so the display package gains a sampler unit, a bash loop that every 5 s reads the `pihero-kiosk` cgroup's `cpu.stat`, `memory.current` and `memory.swap.current` and the web process's ticks from `/proc`, and writes one JSON object to `/run/netmon-display/stats.json`. lighttpd serves that file through a symlink in the web root. The page polls it every 5 s into a store and renders three pills between the title and the "started" text; a missing, failed or stale sample renders nothing.

**Tech Stack:** bash 5 (`EPOCHREALTIME`), systemd (`DynamicUser`, `RuntimeDirectory`), nfpm (`tree`, `symlink`), lighttpd, Kotlin/JS with fritz2 and kotlinx.serialization, Tailwind 3, pytest with testinfra and Playwright.

**Spec:** The brief of 2026-10-03 in the session: "permanent nice looking sampler in the top bar. You can put three badges/pills between 'Network Monitor' and 'started'; also the MQTT::Connected status does [not] need the curly braces details." The measurement background is in [the footprint spec](../specs/2026-10-02-footprint-design.md), follow-up 1 and the Numbers section (web process about 1.1 cores on the board, 5 % in the VM).

## Global Constraints

- Conventional Commits, lowercase imperative header of at most 72 characters, one change per commit, no AI attribution trailers; scopes in use: `display`, `kiosk`, `scanner`, `spec`.
- Tests first, helpers last, no comments in tests ([testing.md](../../../../../.config/agents/rules/testing.md)); the reviewer reads the rules as the user does.
- IDE inspections (`mcp__idea__get_file_problems`, `errorsOnly: false`) on every changed file before its commit.
- One VM, one Gradle build, one container at a time. `make gradle` before `make test-tier2` after a page change. A tier 2 run shadows `netmon.local` for a while: no board step right after it without checking `dscacheutil -q host -a name netmon.local`.
- Ask before every push, pull request, merge and board mutation.
- Tailwind classes are complete static strings, never built from parts.
- The bash script follows [bash.md](../../../../../.config/agents/rules/bash.md): header with Purpose and Usage, named options parsed in a `while`/`case` loop, `-h`/`--help` prints the header, unknown options exit 2, `set -euo pipefail`, no file extension, executable, shebang `#!/usr/bin/env bash`.
- Public declarations in jsMain get a one-line KDoc in the stdlib register ([documentation.md](../../../../../.config/agents/rules/documentation.md)); test code gets none.
- Memory on the board is the scarce resource: the sampler runs under `MemoryMax=16M` and writes only to tmpfs.
- The MB shown are mebibytes labelled `MB`, as the soak tables do (`tests/sampling.py`, `mb()`).

## Review Focus

1. The web process restarts between two samples (cog runs with `--webprocess-failure=restart`): the new PID's ticks start near zero, so a naive delta is negative or huge. Expected: no `webCpu` for that sample. Test in Task 1.
2. lighttpd serves a symlink into tmpfs written by a dynamic user: if symlinks are not followed or the file is unreadable, the panel silently shows nothing. Expected: `GET /stats.json` answers the JSON. Test in Task 2.
3. The sampler is dead while lighttpd still serves its last file: the pills must not show a frozen figure as current. Expected: a sample older than three intervals is dropped. Tests in Tasks 3 and 4.
4. The page runs where there is no sampler (Playwright on the Mac against a local server, the VM before the unit started): the poll fails or 404s. Expected: no pills, no error on the status bar, polling continues. Test in Task 4.
5. The pills must not re-render on every clock tick: the status bar renders them from the sample flow alone, never combined with the per-second clock, since a DOM mutation per second is what the zoom observer reacts to. No automated test; the reviewer checks `status.kt` renders from `kioskStats` only.

---

### Task 1: The sampler script

**Files:**
- Create: `packages/netmon-display/root/usr/lib/netmon/netmon-display-stats` (executable)
- Test: `packages/netmon-display/tests/test_stats.py`

**Interfaces:**
- Produces: the executable `netmon-display-stats [--out <file>] [--interval <seconds>] [--root <dir>] [--once]`, writing one JSON object per sample, `{"at":<epoch seconds>,"interval":<seconds>,"kioskCpu":<int|null>,"webCpu":<int|null>,"kioskMemory":<bytes|null>}`, atomically via `<out>.tmp` and `mv`.

- [ ] **Step 1: Write the failing tests**

```python
import json
import os
import subprocess
import time
from pathlib import Path

import pytest

pytestmark = pytest.mark.tier0
SCRIPT = Path(__file__).resolve().parents[1] / "root" / "usr" / "lib" / "netmon" / "netmon-display-stats"
CLK_TCK = os.sysconf("SC_CLK_TCK")


class TestSample:
    def test_reports_the_kiosks_cpu_the_web_processs_cpu_and_the_memory(self, tmp_path):
        root = kiosk_root(tmp_path, usec=1_000_000, ticks=500, pid=42, ram=160_000_000, swap=8_820_736)

        sample = sample_once(root, tmp_path / "stats.json", lambda: write_counters(root, usec=2_180_000, ticks=500 + int(1.14 * CLK_TCK), pid=42))

        assert sample["interval"] == 1
        assert abs(sample["kioskCpu"] - 118) <= 6
        assert abs(sample["webCpu"] - 114) <= 6
        assert sample["kioskMemory"] == 168_820_736
        assert abs(sample["at"] - time.time()) < 5

    def test_without_the_kiosk_reports_only_the_time(self, tmp_path):
        root = tmp_path / "root"
        (root / "proc").mkdir(parents=True)

        sample = sample_once(root, tmp_path / "stats.json")

        assert {key: value for key, value in sample.items() if key != "at"} == {"interval": 1, "kioskCpu": None, "webCpu": None, "kioskMemory": None}

    def test_a_replaced_web_process_has_no_cpu_figure_for_that_sample(self, tmp_path):
        root = kiosk_root(tmp_path, usec=0, ticks=500, pid=42, ram=1, swap=0)

        sample = sample_once(root, tmp_path / "stats.json", lambda: write_counters(root, usec=100_000, ticks=10, pid=43))

        assert sample["webCpu"] is None
        assert sample["kioskCpu"] is not None


class TestUsage:
    def test_help_prints_the_header(self):
        result = subprocess.run([str(SCRIPT), "--help"], capture_output=True, text=True, check=False)

        assert result.returncode == 0
        assert result.stdout.startswith("Purpose:")

    def test_an_unknown_option_exits_with_two(self):
        result = subprocess.run([str(SCRIPT), "--bogus"], capture_output=True, text=True, check=False)

        assert result.returncode == 2
        assert "--help" in result.stderr


def sample_once(root: Path, out: Path, advance=None) -> dict:
    process = subprocess.Popen([str(SCRIPT), "--root", str(root), "--out", str(out), "--interval", "1", "--once"])
    if advance is not None:
        time.sleep(0.4)
        advance()
    assert process.wait(timeout=10) == 0
    return json.loads(out.read_text())


def kiosk_root(tmp_path: Path, usec: int, ticks: int, pid: int, ram: int, swap: int) -> Path:
    root = tmp_path / "root"
    cgroup = root / "sys/fs/cgroup/system.slice/pihero-kiosk.service"
    cgroup.mkdir(parents=True)
    (cgroup / "memory.current").write_text(f"{ram}\n")
    (cgroup / "memory.swap.current").write_text(f"{swap}\n")
    write_counters(root, usec=usec, ticks=ticks, pid=pid)
    return root


def write_counters(root: Path, usec: int, ticks: int, pid: int) -> None:
    cgroup = root / "sys/fs/cgroup/system.slice/pihero-kiosk.service"
    (cgroup / "cpu.stat").write_text(f"usage_usec {usec}\nuser_usec {usec}\nsystem_usec 0\n")
    (cgroup / "cgroup.procs").write_text(f"1\n{pid}\n")
    proc = root / "proc" / str(pid)
    proc.mkdir(parents=True, exist_ok=True)
    (proc / "comm").write_text("WPEWebProcess\n")
    (proc / "stat").write_text(f"{pid} (WPEWebProcess) S 1 1 1 0 -1 4194560 100 0 0 0 {ticks // 2} {ticks - ticks // 2} 0 0 20 0 8 0 100 0 0 0 0 0 0 0 0\n")
```

The `stat` line puts `utime` and `stime` at fields 14 and 15 of the full line, which are the 12th and 13th words after the parenthesised command name. The first entry `1` in `cgroup.procs` has no `comm` in the fixture, so the script's filter has to skip it.

- [ ] **Step 2: Run the tests to see them fail**

Run: `uv run --frozen pytest packages/netmon-display/tests/test_stats.py -q -p no:cacheprovider`
Expected: 5 failed, each `FileNotFoundError` for the script.

- [ ] **Step 3: Write the script**

```bash
#!/usr/bin/env bash
# Purpose: Samples the kiosk's CPU and memory for the panel's status bar and writes them as JSON.
# Usage:   netmon-display-stats [--out <file>] [--interval <seconds>] [--root <dir>] [--once]
#
# Options:
#   --out <file>          The JSON file to write (default: /run/netmon-display/stats.json).
#   --interval <seconds>  Seconds between two samples (default: 5).
#   --root <dir>          Directory holding sys/ and proc/, for a fixture (default: /).
#   --once                Write one sample and exit.
#   -h, --help            Show this help.
#
# Examples:
#   netmon-display-stats --once --interval 1 --out /tmp/stats.json && cat /tmp/stats.json
#   {"at":1759450000,"interval":1,"kioskCpu":118,"webCpu":114,"kioskMemory":168820736}

set -euo pipefail

usage() { awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "${BASH_SOURCE[0]}"; }
die()   { printf '%s: %s\nSee '\''%s --help'\''\n' "${0##*/}" "$1" "${0##*/}" >&2; exit 2; }

out=/run/netmon-display/stats.json; interval=5; root=/; once=false
while (( $# )); do
  case $1 in
    -h|--help)    usage; exit 0 ;;
    --out)        out=${2?--out: missing value}; shift 2 ;;
    --out=*)      out=${1#*=}; shift ;;
    --interval)   interval=${2?--interval: missing value}; shift 2 ;;
    --interval=*) interval=${1#*=}; shift ;;
    --root)       root=${2?--root: missing value}; shift 2 ;;
    --root=*)     root=${1#*=}; shift ;;
    --once)       once=true; shift ;;
    -?*)          die "unknown option: $1" ;;
    *)            die "unexpected argument: $1" ;;
  esac
done

cgroup="${root%/}/sys/fs/cgroup/system.slice/pihero-kiosk.service"
proc="${root%/}/proc"
tick=$(getconf CLK_TCK)

# Microseconds since the epoch.
now_usec() { local t=$EPOCHREALTIME; echo "${t%.*}${t#*.}"; }

# The kiosk cgroup's CPU time in microseconds, empty without the cgroup.
kiosk_usec() { awk '/^usage_usec/ {print $2}' "$cgroup/cpu.stat" 2>/dev/null || true; }

# The kiosk cgroup's RAM plus zram in bytes, empty without the cgroup.
kiosk_memory() {
  local ram swap
  ram=$(cat "$cgroup/memory.current" 2>/dev/null) || return 0
  swap=$(cat "$cgroup/memory.swap.current" 2>/dev/null) || swap=0
  echo $(( ram + swap ))
}

# The PID of the kiosk's web process, empty without one.
web_pid() {
  local pid
  for pid in $(cat "$cgroup/cgroup.procs" 2>/dev/null); do
    if [[ $(cat "$proc/$pid/comm" 2>/dev/null) == WPEWebProcess ]]; then echo "$pid"; return; fi
  done
}

# The CPU ticks of PID $1 in user and kernel mode, empty when it is gone.
web_ticks() {
  local stat
  stat=$(cat "$proc/$1/stat" 2>/dev/null) || return 0
  # shellcheck disable=SC2086  # the words after the parenthesised command name are meant to split
  set -- ${stat##*) }
  echo $(( ${12} + ${13} ))
}

json() { printf '{"at":%s,"interval":%s,"kioskCpu":%s,"webCpu":%s,"kioskMemory":%s}\n' "$1" "$interval" "${2:-null}" "${3:-null}" "${4:-null}"; }

prev_time=$(now_usec); prev_usec=$(kiosk_usec); prev_pid=$(web_pid); prev_ticks=$(web_ticks "$prev_pid")
while :; do
  sleep "$interval"
  time=$(now_usec); usec=$(kiosk_usec); pid=$(web_pid); ticks=$(web_ticks "$pid")
  elapsed=$(( time - prev_time ))
  kiosk_cpu=""; web_cpu=""
  if [[ -n $usec && -n $prev_usec ]]; then kiosk_cpu=$(( (usec - prev_usec) * 100 / elapsed )); fi
  if [[ -n $pid && $pid == "$prev_pid" && -n $ticks && -n $prev_ticks ]]; then
    web_cpu=$(( (ticks - prev_ticks) * 100 * 1000000 / (tick * elapsed) ))
  fi
  json "$(( time / 1000000 ))" "$kiosk_cpu" "$web_cpu" "$(kiosk_memory)" > "$out.tmp" && mv -f "$out.tmp" "$out"
  prev_time=$time; prev_usec=$usec; prev_pid=$pid; prev_ticks=$ticks
  if $once; then exit 0; fi
done
```

Then `chmod +x packages/netmon-display/root/usr/lib/netmon/netmon-display-stats`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `uv run --frozen pytest packages/netmon-display/tests/test_stats.py -q -p no:cacheprovider`
Expected: 5 passed in about 4 s.

- [ ] **Step 5: Lint and inspect**

Run: `shellcheck packages/netmon-display/root/usr/lib/netmon/netmon-display-stats` if shellcheck is installed, else skip and say so. Then `mcp__idea__get_file_problems` on the script and the test, `errorsOnly: false`.

- [ ] **Step 6: Commit**

```bash
git add packages/netmon-display/root/usr/lib/netmon/netmon-display-stats packages/netmon-display/tests/test_stats.py
git commit -m "feat(display): sample the kiosk's cpu and memory as json"
```

---

### Task 2: The sampler unit, served by lighttpd

**Files:**
- Create: `packages/netmon-display/root/usr/lib/systemd/system/netmon-display-stats.service`
- Create: `packages/netmon-display/units.txt`
- Modify: `packages/netmon-display/nfpm.yaml` (the `contents` list)
- Test: `packages/netmon-display/tests/test_installed.py`

**Interfaces:**
- Consumes: the script of Task 1 at `/usr/lib/netmon/netmon-display-stats`.
- Produces: `GET http://localhost/stats.json` on an installed system answers the sampler's JSON within a few seconds of boot; the unit `netmon-display-stats.service` is enabled and running.

- [ ] **Step 1: Write the failing tests**

Add `import json` at the top of `packages/netmon-display/tests/test_installed.py`, and after `TestKiosk`:

```python
class TestStats:
    def test_the_sampler_runs_as_an_enabled_unit(self, host):
        unit = host.service("netmon-display-stats")

        assert unit.is_enabled
        assert unit.is_running

    def test_serves_the_latest_sample_as_json(self, host):
        text = fetch_until(host, "http://localhost/stats.json", '"at"')

        sample = json.loads(text)
        assert set(sample) == {"at", "interval", "kioskCpu", "webCpu", "kioskMemory"}
        assert sample["interval"] == 5
        if not host.service("pihero-kiosk").is_running:
            assert sample["kioskCpu"] is None
            assert sample["webCpu"] is None
```

In `TestRemoval.test_purge_gives_lighttpd_its_root_back`, add before `target.reinstall()`:

```python
        assert not host.file("/usr/lib/systemd/system/netmon-display-stats.service").exists
```

- [ ] **Step 2: Run tier 1 to see them fail**

Run: `make test-tier1 2>&1 | tail -15`
Expected: the two `TestStats` tests fail, the unit unknown and the fetch returning no `"at"`. One container, about a minute.

- [ ] **Step 3: Write the unit, the units list and the package entries**

`packages/netmon-display/root/usr/lib/systemd/system/netmon-display-stats.service`:

```ini
[Unit]
Description=Netmon: samples the kiosk's CPU and memory for the panel's status bar
Documentation=https://github.com/bkahlert/netmon
After=pihero-kiosk.service

[Service]
DynamicUser=yes
RuntimeDirectory=netmon-display
ExecStart=/usr/lib/netmon/netmon-display-stats
Restart=always
RestartSec=10
Nice=10
MemoryMax=16M
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
PrivateTmp=yes

[Install]
WantedBy=multi-user.target
```

`packages/netmon-display/units.txt`:

```
netmon-display-stats.service
```

In `packages/netmon-display/nfpm.yaml`, the `contents` list becomes:

```yaml
contents:
  - src: root/
    dst: /
    type: tree
  - src: ../../build/dist/js/productionExecutable/
    dst: /usr/share/netmon/web/
    type: tree
  - src: /run/netmon-display/stats.json
    dst: /usr/share/netmon/web/stats.json
    type: symlink
  - src: conf/lighttpd-netmon.conf
    dst: /etc/lighttpd/conf-available/90-netmon.conf
    type: config
```

The testkit turns `units.txt` into the debhelper snippet that enables and starts the unit on install and stops and masks it on removal (`pihero_testkit/maintscripts.py`), so the display's own `postinst.sh` and `prerm.sh` fragments stay as they are.

- [ ] **Step 4: Run tier 1 to see them pass**

Run: `make test-tier1 2>&1 | tail -15`
Expected: `18 passed, 3 skipped`. If `test_serves_the_latest_sample_as_json` fails with an empty body, check inside a kept container (`uv run --frozen pytest -m installed --target=podman --keep ...`) whether `/run/netmon-display/stats.json` exists and is world-readable and whether lighttpd follows the symlink (`curl -sI http://localhost/stats.json`); Debian's lighttpd follows symlinks by default.

- [ ] **Step 5: Inspect**

`mcp__idea__get_file_problems` on the unit, `units.txt`, `nfpm.yaml` and the test, `errorsOnly: false`.

- [ ] **Step 6: Commit**

```bash
git add packages/netmon-display/root/usr/lib/systemd/system/netmon-display-stats.service packages/netmon-display/units.txt packages/netmon-display/nfpm.yaml packages/netmon-display/tests/test_installed.py
git commit -m "feat(display): run the sampler as a unit and serve its json"
```

---

### Task 3: The sample in the page

**Files:**
- Create: `src/jsMain/kotlin/com/bkahlert/netmon/KioskStats.kt`
- Test: `src/jsTest/kotlin/com/bkahlert/netmon/KioskStatsTest.kt`

**Interfaces:**
- Produces: `@Serializable data class KioskStats(val at: Long, val interval: Int, val kioskCpu: Int? = null, val webCpu: Int? = null, val kioskMemory: Long? = null)` with `fun isFreshAt(now: Instant): Boolean` and `KioskStats.INTERVAL: Duration = 5.seconds`; top-level `fun cpuText(percent: Int): String` and `fun memoryText(bytes: Long): String`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.bkahlert.netmon

import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.time.Instant

class KioskStatsTest {

    @Test
    fun decodes_the_samplers_json() {
        val stats = JsonFormat.decodeFromString<KioskStats>("""{"at":1759450000,"interval":5,"kioskCpu":118,"webCpu":114,"kioskMemory":168820736}""")

        stats shouldBe KioskStats(at = 1759450000, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
    }

    @Test
    fun decodes_absent_sources_as_null() {
        val stats = JsonFormat.decodeFromString<KioskStats>("""{"at":1759450000,"interval":5,"kioskCpu":null,"webCpu":null,"kioskMemory":null}""")

        stats shouldBe KioskStats(at = 1759450000, interval = 5)
    }

    @Test
    fun is_fresh_for_less_than_three_intervals() {
        val stats = KioskStats(at = 1_000, interval = 5)

        stats.isFreshAt(Instant.fromEpochSeconds(1_014)) shouldBe true
        stats.isFreshAt(Instant.fromEpochSeconds(1_015)) shouldBe false
    }

    @Test
    fun formats_cpu_and_memory_as_the_panel_shows_them() {
        cpuText(114) shouldBe "114 %"
        memoryText(168_820_736) shouldBe "161 MB"
    }
}
```

- [ ] **Step 2: Run the JS tests to see them fail**

Run: `./gradlew -q jsBrowserTest 2>&1 | grep -E '^e:' | head -5`
Expected: compile errors, `Unresolved reference 'KioskStats'`.

- [ ] **Step 3: Write the class**

```kotlin
package com.bkahlert.netmon

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A sample of the kiosk written by `netmon-display-stats`: taken [at] the epoch second, [interval] seconds after the one
 * before; [kioskCpu] and [webCpu] in percent of one core, [kioskMemory] RAM plus zram in bytes, each `null` when its
 * source was absent.
 */
@Serializable
data class KioskStats(
    val at: Long,
    val interval: Int,
    val kioskCpu: Int? = null,
    val webCpu: Int? = null,
    val kioskMemory: Long? = null,
) {
    /** Returns whether the sample is less than three intervals old at [now]. */
    fun isFreshAt(now: Instant): Boolean = now - Instant.fromEpochSeconds(at) < interval.seconds * 3

    companion object {
        /** The sampler's interval, which the page polls at as well. */
        val INTERVAL: Duration = 5.seconds
    }
}

/** Returns [percent] as the panel shows a CPU share, `114 %`. */
fun cpuText(percent: Int): String = "$percent %"

/** Returns [bytes] as the panel shows memory, whole mebibytes labelled MB as the soak tables do, `161 MB`. */
fun memoryText(bytes: Long): String = "${bytes / 1_048_576} MB"
```

- [ ] **Step 4: Run the JS tests to see them pass**

Run: `./gradlew -q jsBrowserTest 2>&1 | grep -E '^e:|FAILED' | head -5; echo "exit=${pipestatus[1]}"`
Expected: no output, `exit=0`. Confirm the four tests ran: `grep -c testcase build/test-results/jsBrowserTest/*KioskStatsTest.xml` gives 4.

- [ ] **Step 5: Inspect and commit**

`mcp__idea__get_file_problems` on both files, `errorsOnly: false`. Then:

```bash
git add src/jsMain/kotlin/com/bkahlert/netmon/KioskStats.kt src/jsTest/kotlin/com/bkahlert/netmon/KioskStatsTest.kt
git commit -m "feat(display): decode and format the kiosk's sample"
```

---

### Task 4: Polling the sample

**Files:**
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/stores.kt` (after `CurrentTimeStore`)
- Test: `src/jsTest/kotlin/com/bkahlert/netmon/KioskStatsStoreTest.kt`

**Interfaces:**
- Consumes: `KioskStats`, `KioskStats.INTERVAL`, `isFreshAt` from Task 3.
- Produces: `class KioskStatsStore(interval: Duration = KioskStats.INTERVAL, load: suspend () -> KioskStats? = ::loadKioskStats, clock: () -> Instant = Clock.System::now, job: Job = Job()) : RootStore<KioskStats?>` whose `data` is the latest fresh sample or `null`; `suspend fun loadKioskStats(): KioskStats?`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.bkahlert.netmon

import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class KioskStatsStoreTest {

    @Test
    fun holds_the_latest_sample_and_drops_it_when_a_poll_fails() = runTest {
        val polls = Channel<KioskStats?>()
        val store = KioskStatsStore(interval = 1.milliseconds, load = { polls.receive() }, clock = { Instant.fromEpochSeconds(1_006) }, job = job)

        polls.send(sample(at = 1_000))
        delay(10)
        store.current shouldBe sample(at = 1_000)
        polls.send(sample(at = 1_005))
        delay(10)
        store.current shouldBe sample(at = 1_005)
        polls.send(null)
        delay(10)

        store.current shouldBe null
    }

    @Test
    fun drops_a_sample_older_than_three_intervals() = runTest {
        val polls = Channel<KioskStats?>()
        val store = KioskStatsStore(interval = 1.milliseconds, load = { polls.receive() }, clock = { Instant.fromEpochSeconds(1_015) }, job = job)

        polls.send(sample(at = 1_000))
        delay(10)

        store.current shouldBe null
    }
}

private fun sample(at: Long): KioskStats = KioskStats(at = at, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736)
```

`com.bkahlert.netmon.fritz2.runTest` is the project's runner that supplies `job`, as `StoresTest` uses it; if it does not expose `job`, use `kotlinx.coroutines.test.runTest` and pass `job = Job()` the way `CurrentTimeStore` does.

- [ ] **Step 2: Run the JS tests to see them fail**

Run: `./gradlew -q jsBrowserTest 2>&1 | grep -E '^e:' | head -5`
Expected: `Unresolved reference 'KioskStatsStore'`.

- [ ] **Step 3: Write the store and the loader**

Append to `src/jsMain/kotlin/com/bkahlert/netmon/stores.kt` after `CurrentTimeStore`, adding the imports `kotlinx.browser.window`, `kotlinx.coroutines.await`, `kotlinx.serialization.decodeFromString`, `org.w3c.fetch.NO_STORE`, `org.w3c.fetch.RequestCache`, `org.w3c.fetch.RequestInit`, `com.bkahlert.netmon.serialization.JsonFormat`, `kotlin.time.Clock`, `kotlin.time.Instant` where missing:

```kotlin
/** Store of the kiosk's latest fresh sample, `null` while there is none, polled every [interval] through [load]. */
class KioskStatsStore(
    interval: Duration = KioskStats.INTERVAL,
    load: suspend () -> KioskStats? = ::loadKioskStats,
    clock: () -> Instant = Clock.System::now,
    job: Job = Job(),
) : RootStore<KioskStats?>(null, job = job) {

    init {
        flow {
            while (true) {
                emit(load()?.takeIf { it.isFreshAt(clock()) })
                delay(interval)
            }
        } handledBy update
    }
}

/** Returns the sample lighttpd serves next to the page, or `null` when it is missing, unreadable or not a sample. */
suspend fun loadKioskStats(): KioskStats? = runCatching {
    val response = window.fetch("stats.json", RequestInit(cache = RequestCache.NO_STORE)).await()
    if (response.ok) JsonFormat.decodeFromString<KioskStats>(response.text().await()) else null
}.getOrNull()
```

- [ ] **Step 4: Run the JS tests to see them pass**

Run: `./gradlew -q jsBrowserTest 2>&1 | grep -E '^e:|FAILED' | head -5; echo "exit=${pipestatus[1]}"`
Expected: `exit=0`; `build/test-results/jsBrowserTest/*KioskStatsStoreTest.xml` lists 2 testcases without failures.

- [ ] **Step 5: Inspect and commit**

`mcp__idea__get_file_problems` on `stores.kt` and the test, `errorsOnly: false`. Then:

```bash
git add src/jsMain/kotlin/com/bkahlert/netmon/stores.kt src/jsTest/kotlin/com/bkahlert/netmon/KioskStatsStoreTest.kt
git commit -m "feat(display): poll the kiosk's sample next to the page"
```

---

### Task 5: The pills in the status bar, and the connect line without its packet

**Files:**
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/ui/status.kt`
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/app.kt:23-26`
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/ui/events.kt:33`
- Test: `src/jsTest/kotlin/com/bkahlert/netmon/ui/StatusKtTest.kt`

**Interfaces:**
- Consumes: `KioskStats`, `cpuText`, `memoryText` (Task 3), `KioskStatsStore` (Task 4).
- Produces: `fun RenderContext.status(consoleLogStore: ConsoleLogStore, kioskStats: Flow<KioskStats?>)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.bkahlert.netmon.ui

import com.bkahlert.netmon.ConsoleLogStore
import com.bkahlert.netmon.KioskStats
import com.bkahlert.netmon.fritz2.runTest
import dev.fritz2.core.render
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.HTMLElement
import kotlin.test.Test

class StatusKtTest {

    @Test
    fun shows_the_kiosks_sample_as_pills_between_the_title_and_the_start() = runTest {
        val text = statusText(KioskStats(at = 1_000, interval = 5, kioskCpu = 118, webCpu = 114, kioskMemory = 168_820_736))

        text shouldContain "web 114 %"
        text shouldContain "kiosk 118 %"
        text shouldContain "kiosk 161 MB"
        text.indexOf("web 114 %") shouldBeGreaterThan text.indexOf("Network Monitor")
        text.indexOf("kiosk 161 MB") shouldBeLessThan text.indexOf("started")
    }

    @Test
    fun shows_no_pills_without_a_sample() = runTest {
        val text = statusText(null)

        text shouldNotContain "%"
        text shouldNotContain "MB"
    }

    @Test
    fun leaves_out_the_pill_of_an_absent_source() = runTest {
        val text = statusText(KioskStats(at = 1_000, interval = 5, kioskCpu = 118, kioskMemory = 168_820_736))

        text shouldNotContain "web"
        text shouldContain "kiosk 118 %"
    }
}

private suspend fun statusText(stats: KioskStats?): String {
    val container = document.createElement("div") as HTMLElement
    document.body?.appendChild(container)
    render(container) { status(ConsoleLogStore("info" to "Starting..."), MutableStateFlow(stats)) }
    delay(50)
    val text = container.textContent.orEmpty()
    container.remove()
    return text
}
```

- [ ] **Step 2: Run the JS tests to see them fail**

Run: `./gradlew -q jsBrowserTest 2>&1 | grep -E '^e:' | head -5`
Expected: `No value passed for parameter 'kioskStats'` or a type mismatch on `status`.

- [ ] **Step 3: Render the pills**

`src/jsMain/kotlin/com/bkahlert/netmon/ui/status.kt` becomes:

```kotlin
package com.bkahlert.netmon.ui

import com.bkahlert.netmon.ConsoleLogStore
import com.bkahlert.netmon.CurrentTimeStore
import com.bkahlert.netmon.KioskStats
import com.bkahlert.netmon.cpuText
import com.bkahlert.netmon.memoryText
import dev.fritz2.core.RenderContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

fun RenderContext.status(consoleLogStore: ConsoleLogStore, kioskStats: Flow<KioskStats?>) {
    h1("font-bold") { +"Network Monitor" }
    div("flex items-baseline gap-1 empty:hidden") {
        kioskStats.render(this) { stats ->
            if (stats != null) {
                stats.webCpu?.let { pill("web", cpuText(it), "CPU of the web process, share of one core over the last ${stats.interval} s") }
                stats.kioskCpu?.let { pill("kiosk", cpuText(it), "CPU of cog and its WebKit processes, share of one core over the last ${stats.interval} s") }
                stats.kioskMemory?.let { pill("kiosk", memoryText(it), "RAM plus zram of cog and its WebKit processes") }
            }
        }
    }
    div("opacity-50") {
        +"started "
        span {
            val start = Clock.System.now()
            CurrentTimeStore.data
                .map { now -> (start - now).toMomentString() }
                .renderText(into = this)
        }
    }
    div("flex-1 text-right truncate font-mono") {
        // Always show the last (relevant) log message at the top of the app.
        consoleLogStore.data.render(this) { (fn, message) ->
            span(
                when (fn) {
                    "error" -> "text-red-500"
                    "warn" -> "text-yellow-500 opacity-75"
                    else -> "opacity-50"
                }
            ) { +message }
        }
    }
}

private fun RenderContext.pill(label: String, value: String, explanation: String) {
    span("rounded-full border border-slate-100/25 px-1.5 leading-none tabular-nums") {
        title(explanation)
        span("opacity-50") { +"$label " }
        +value
    }
}
```

The pills are quiet on purpose: the bar's own 12 px size, a hairline border in the text colour at a quarter opacity, the label dimmed, tabular figures so the numbers do not jitter as they change. The host cards stay the panel's one loud element.

In `src/jsMain/kotlin/com/bkahlert/netmon/app.kt`, the status render becomes:

```kotlin
    render(statusSelector) {
        val consoleLogStore = ConsoleLogStore("info" to "Starting...")
        status(consoleLogStore, KioskStatsStore().data)
    }
```

with `import com.bkahlert.netmon.KioskStatsStore` if the package differs (it does not, both are `com.bkahlert.netmon`, so no import is needed).

In `src/jsMain/kotlin/com/bkahlert/netmon/ui/events.kt`, line 33 becomes:

```kotlin
        onConnect { com.bkahlert.kommons.js.console.info("MQTT::Connected") }
```

- [ ] **Step 4: Run the JS tests to see them pass**

Run: `./gradlew -q jsBrowserTest 2>&1 | grep -E '^e:|FAILED' | head -5; echo "exit=${pipestatus[1]}"`
Expected: `exit=0`; the `StatusKtTest` results file lists 3 testcases without failures; the total is 87 + 4 + 2 + 3 = 96 tests.

- [ ] **Step 5: Look at it**

Run: `make gradle` (one Gradle build; the jar is unchanged, so the native image is not rebuilt), then serve the bundle and open it in Playwright's WebKit at the panel's size:

```bash
(cd build/dist/js/productionExecutable && python3 -m http.server --bind 127.0.0.1 8765 >/dev/null 2>&1 &)
printf '{"at":%s,"interval":5,"kioskCpu":118,"webCpu":114,"kioskMemory":168820736}\n' "$(date +%s)" > build/dist/js/productionExecutable/stats.json
uv run --frozen python - <<'EOF'
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    page = p.webkit.launch().new_page(viewport={"width": 800, "height": 480})
    page.goto("http://127.0.0.1:8765/")
    page.wait_for_timeout(1500)
    page.screenshot(path="dist/status-pills.png")
EOF
pkill -f 'http.server --bind 127.0.0.1 8765'; rm build/dist/js/productionExecutable/stats.json
```

Read `dist/status-pills.png`: three pills sit between the title and "started", baseline-aligned, the bar still one line. Adjust the pill classes only if the picture says so, and re-run Step 4 afterwards.

- [ ] **Step 6: Inspect and commit**

`mcp__idea__get_file_problems` on `status.kt`, `app.kt`, `events.kt` and the test, `errorsOnly: false`. Two commits:

```bash
git add src/jsMain/kotlin/com/bkahlert/netmon/ui/status.kt src/jsMain/kotlin/com/bkahlert/netmon/app.kt src/jsTest/kotlin/com/bkahlert/netmon/ui/StatusKtTest.kt
git commit -m "feat(display): show the kiosk's cpu and memory as pills in the status bar"
git add src/jsMain/kotlin/com/bkahlert/netmon/ui/events.kt
git commit -m "fix(display): log the broker connect without its packet"
```

---

### Task 6: The sampler and the pills on a booted system

**Files:**
- Modify: `tests/test_boot.py` (class `TestKiosk`, and a helper at the bottom)
- Modify: `tests/test_display.py` (class `TestDisplay`)

**Interfaces:**
- Consumes: `GET /stats.json` (Task 2) and the pills (Task 5).

- [ ] **Step 1: Write the tests**

In `tests/test_boot.py`, add `import json` and `import time` to the imports if missing, then in `TestKiosk` after `test_the_web_process_sees_the_webkit_variables`:

```python
    def test_the_sampler_reports_the_web_process(self, host):
        if not host.file("/dev/dri").exists:
            pytest.skip("no display adapter")

        sample = sample_until(host, lambda s: s.get("webCpu") is not None)

        assert sample["kioskCpu"] is not None
        assert sample["kioskMemory"] > 0
```

At the bottom of `tests/test_boot.py`, after the existing helpers:

```python
def sample_until(host, ready, attempts: int = 20) -> dict:
    sample = {}
    for _ in range(attempts):
        text = host.run("python3 -c 'import urllib.request; print(urllib.request.urlopen(\"http://localhost/stats.json\", timeout=5).read().decode())'").stdout
        if text.strip():
            sample = json.loads(text)
            if ready(sample):
                return sample
        time.sleep(1)
    return sample
```

In `tests/test_display.py`, add `import re` and in `TestDisplay` after the existing test:

```python
    def test_shows_the_kiosks_load_in_the_status_bar(self, page, tunnel):
        page.goto(f"http://127.0.0.1:{tunnel.http}/?broker.host=127.0.0.1&broker.port={tunnel.ws}")

        pill = page.locator(".status span", has_text=re.compile(r"^web \d+ %$"))

        pill.first.wait_for(timeout=20_000)
        assert page.locator(".status span", has_text=re.compile(r"^kiosk \d+ MB$")).count() == 1
```

- [ ] **Step 2: Run tier 2**

Run: `make gradle && make test-tier2 2>&1 | tail -12` (one VM, about three minutes; `make gradle` first so the VM gets the new bundle and package).
Expected: `30 passed, 1 skipped`. On failure of the sampler test, `make vm` and read `journalctl -u netmon-display-stats` and `cat /run/netmon-display/stats.json` in the VM; on failure of the pill test, read `dist/tier2/display.png`.

- [ ] **Step 3: Look at the kiosk**

Read `dist/tier2/kiosk.png` and `dist/tier2/display.png`: the pills are on the bar in both, the connect line reads `MQTT::Connected` without braces.

- [ ] **Step 4: Inspect and commit**

`mcp__idea__get_file_problems` on both test files, `errorsOnly: false`. Then:

```bash
git add tests/test_boot.py tests/test_display.py
git commit -m "test(kiosk): assert the sampler's figures and the pills on the booted system"
```

---

### Task 7: README, the whole suite, the pull request

**Files:**
- Modify: `README.md:32` (the sentence naming the two packages)

- [ ] **Step 1: Document the readout**

After the sentence in `README.md` that ends with "shown full screen by `pihero-kiosk`).", add:

```markdown
The panel's status bar shows the kiosk's CPU and memory, sampled every 5 s by the display package's `netmon-display-stats` unit.
```

`mcp__idea__get_file_problems` on `README.md`, then:

```bash
git add README.md
git commit -m "docs(readme): name the status bar's kiosk readout"
```

- [ ] **Step 2: Run everything once more**

Run in this order, one at a time: `make test-tier0`, `make test-tier1`, `./gradlew -q jsBrowserTest jvmTest`, `make test-tier2`.
Expected: tier 0 `108 passed` (103 plus the 5 of Task 1), tier 1 `18 passed, 3 skipped`, JS 96 tests and JVM green, tier 2 `30 passed, 1 skipped`.

- [ ] **Step 3: Ask, then push and open the pull request**

Ask the user. On yes: `git push -u origin feat/kiosk-stats`, `gh pr create` with the title `feat(display): show the kiosk's cpu and memory in the status bar` and a body listing the commits and the tier results, then `gh pr checks <n> --watch && gh pr merge <n> --rebase`.

- [ ] **Step 4: After the merge**

Record in the footprint spec's Numbers section, in one sentence, that the sampler's unit adds to the "everything else" term and that the next board soak after the release measures it. The release itself (`make release VERSION=2.1.0`, the tag push, the board's apt upgrade, one kiosk restart) is the user's call and not part of this plan.
