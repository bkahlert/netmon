# The Footprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shrink the scanner and the kiosk until `apt-get update` and `apt-get install --reinstall` run on the live
board next to both units, then retire the apt hook.

**Architecture:** A soak and an apt probe in the pytest testkit measure every step in the VM first and on the board
second. The scanner loses HiveMQ, logback and the Python converter for Paho, slf4j-simple and a StAX parser, then
becomes a GraalVM native image built in a container and packaged for arm64 only. The kiosk keeps cog and the page; cog
flags and JavaScriptCore variables arrive through the device file, and the page polls, animates and ticks less.

**Tech Stack:** Kotlin 2.4.20 Multiplatform (jvm, js), Gradle with the shadow plugin, fritz2 1.0-RC21, pytest with
pihero-testkit v2.4.0 and testinfra, podman, GraalVM Community 25 native-image, Eclipse Paho MQTT 3 1.2.5, slf4j-simple
2.0.20, nfpm, GitHub Actions on `ubuntu-24.04-arm`.

**Spec:** [docs/superpowers/specs/2026-10-02-footprint-design.md](../specs/2026-10-02-footprint-design.md)

## Global Constraints

- The testkit stays pinned at v2.4.0; no pihero change.
- `netmon-scanner` becomes `arch: arm64`; `linux/arm/v7` leaves CI and the release.
- The native image is built by the `native-image` command inside
  `ghcr.io/graalvm/native-image-community:25@sha256:649f9d929e30f8d4c7672b32426674817b687f759c61a5ed721afe73524afba2`
  (the arm64 manifest), always with `--platform linux/arm64`, with `-march=compatibility`.
- The markers `soak` and `apt` are never selected by `installed or boot`.
- The apt probe's victim is `netmon-display`; it refuses while `/etc/apt/apt.conf.d/52netmon-dpkg` exists on the target.
- Commits: Conventional Commits, lowercase imperative header of at most 72 characters, one change per commit, no AI
  attribution trailers. Branches `<type>/<slug>`, never on `main`, merged by pull request after CI.
- Tests first in a file, helpers last, no comments in tests. Kotlin tests use `kotlin.test` with kotest matchers as the
  existing tests do; Python tests use pytest classes.
- One Gradle build at a time; the IDE's Gradle stays idle during CLI builds.
- Nothing leaves the Mac without asking: no push, no PR, no board mutation unless the task says the user has agreed.
- Before `apt` on the live board, stop `pihero-kiosk` and `netmon-scanner`, except in Task 21, which is exactly about
  not stopping them.
- Never remote-control the user's own browser; Playwright's WebKit is the only browser automation.
- The board snapshot of 2026-10-02 08:50: `/proc/pressure/memory` does not exist, both units' `memory.max` exist (the
  controller is on), armhf is a foreign architecture with zero packages installed, netmon 1.1.1 is installed, the hook
  file exists, `cog --help-all` lists `--doc-viewer`, `--web-mem-limit`, `--web-check-interval`, `--web-kill-threshold`,
  `--webprocess-failure` and `--enable-developer-extras`.

## Review Focus

1. nmap's XML begins with `<!DOCTYPE nmaprun>`; a parser that resolves a DTD would hang or fail offline. Test in Task 5:
   a DOCTYPE with an unreachable `SYSTEM` id parses without touching it.
2. A host element with only a MAC address, which nmap emits for some ARP answers; the parser must leave it out rather
   than fail. Test in Task 5.
3. `systemctl show` prints `MemoryCurrent=[not set]` when the memory controller is off, and the board has no
   `/proc/pressure/memory`; the sampler must report "n/a", not crash. Tests in Task 1.
4. The board resets or drops the SSH connection during the apt probe; the outcome must say "rebooted" or "unreachable",
   not "timeout". Tests in Task 3.
5. A scan timestamp in the future, from clock skew, must count as fresh, not dated. Test in Task 16.

---

## Phase 1: the harness

Branch `test/soak-and-apt-probe` from `origin/main` once the spec's pull request has merged.

### Task 1: the sampler's parsers and the table

**Files:**
- Create: `tests/sampling.py`
- Test: `tests/test_sampling.py`

**Interfaces:**
- Produces: `UNITS`, `KIOSK`, `SCANNER`, `UnitSample`, `SystemSample`, `Sample`, `parse_duration(text) -> int`,
  `parse_show(output) -> dict[str, str]`, `parse_key_values(text) -> dict[str, int]`, `parse_kb_lines(text) -> dict[str,
  int]`, `parse_pressure(text) -> float | None`, `parse_zram(text) -> int | None`, `bytes_or_none(value) -> int | None`,
  `read_sample(host) -> Sample`, `render_table(samples, limits) -> str`, `render_summary(samples) -> str`.

- [ ] **Step 1: Write the failing tests**

```python
import pytest

from sampling import (
    KIOSK,
    SCANNER,
    Sample,
    SystemSample,
    UnitSample,
    bytes_or_none,
    parse_duration,
    parse_kb_lines,
    parse_key_values,
    parse_pressure,
    parse_show,
    parse_zram,
    render_summary,
    render_table,
)

pytestmark = pytest.mark.tier0


class TestParseDuration:
    def test_minutes_seconds_hours_and_bare_seconds(self):
        assert [parse_duration(t) for t in ("10m", "30s", "1h", "90")] == [600, 30, 3600, 90]

    def test_rejects_an_unknown_unit(self):
        with pytest.raises(ValueError):
            parse_duration("5d")


class TestParseShow:
    def test_splits_on_the_first_equals_sign(self):
        result = parse_show("ActiveState=active\nNRestarts=0\nMemoryCurrent=46403584\n")

        assert result == {"ActiveState": "active", "NRestarts": "0", "MemoryCurrent": "46403584"}


class TestBytesOrNone:
    def test_not_set_and_missing_are_none(self):
        assert bytes_or_none("[not set]") is None
        assert bytes_or_none(None) is None
        assert bytes_or_none("") is None

    def test_a_number_is_an_int(self):
        assert bytes_or_none("46403584") == 46403584


class TestParseKeyValues:
    def test_memory_stat_and_vmstat_lines(self):
        assert parse_key_values("anon 29335552\nfile 14327808\n") == {"anon": 29335552, "file": 14327808}
        assert parse_key_values("pswpin 117856217\npgmajfault 126517772\n") == {"pswpin": 117856217, "pgmajfault": 126517772}


class TestParseKbLines:
    def test_meminfo_and_smaps_rollup_lines_become_bytes(self):
        result = parse_kb_lines("MemAvailable:      95312 kB\nSwapFree:         170000 kB\nPrivate_Dirty:     62012 kB\n")

        assert result == {"MemAvailable": 95312 * 1024, "SwapFree": 170000 * 1024, "Private_Dirty": 62012 * 1024}


class TestParsePressure:
    def test_full_avg10(self):
        text = "some avg10=1.50 avg60=0.80 avg300=0.40 total=1234\nfull avg10=0.75 avg60=0.30 avg300=0.10 total=567\n"

        assert parse_pressure(text) == 0.75

    def test_missing_file_is_none(self):
        assert parse_pressure(None) is None
        assert parse_pressure("") is None


class TestParseZram:
    def test_mem_used_total_is_the_third_field(self):
        assert parse_zram("240640000 62609626 69357568        0 111222784       75 11928529     4924    55706\n") == 69357568

    def test_missing_is_none(self):
        assert parse_zram(None) is None


class TestRenderTable:
    def test_has_a_row_per_sample_with_deltas_and_na_for_missing_values(self):
        samples = [sample(0, pswpin=100, pressure=None), sample(30, pswpin=160, pressure=0.5)]

        result = render_table(samples, {SCANNER: "335544320", KIOSK: "314572800"})

        assert "| 0 |" in result and "| 30 |" in result
        assert "| 60 |" in result
        assert "n/a" in result
        assert "335544320" in result

    def test_summary_names_the_peaks(self):
        samples = [sample(0, kiosk_current=80 * 2**20), sample(30, kiosk_current=120 * 2**20)]

        result = render_summary(samples)

        assert "kiosk" in result and "120 MB" in result


def sample(at: float, pswpin: int = 0, pressure: float | None = None, kiosk_current: int = 50 * 2**20) -> Sample:
    scanner = UnitSample(active="active", restarts=0, current=40 * 2**20, swap_current=30 * 2**20, peak=60 * 2**20, swap_peak=40 * 2**20, anon=30 * 2**20, file=10 * 2**20, oom_kills=0)
    kiosk = UnitSample(active="active", restarts=0, current=kiosk_current, swap_current=100 * 2**20, peak=150 * 2**20, swap_peak=120 * 2**20, anon=50 * 2**20, file=20 * 2**20, oom_kills=0)
    system = SystemSample(mem_available=90 * 2**20, swap_free=160 * 2**20, load1=3.1, pswpin=pswpin, pswpout=0, pgmajfault=0, pressure_full10=pressure, zram_used=69 * 2**20, web_private_dirty=60 * 2**20, web_swap=110 * 2**20, top="")
    return Sample(at=at, boot_id="b", units={SCANNER: scanner, KIOSK: kiosk}, system=system)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `uv run --frozen pytest tests/test_sampling.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'sampling'`

- [ ] **Step 3: Write the module**

```python
"""Readers for the soak and the apt probe: systemd's memory properties, the unit cgroups, /proc counters, and a Markdown table."""
from dataclasses import dataclass

SCANNER = "netmon-scanner.service"
KIOSK = "pihero-kiosk.service"
UNITS = (SCANNER, KIOSK)
WEB_PROCESS = "WPEWebProcess"
NOT_SET = "[not set]"
UNIT_SECONDS = {"s": 1, "m": 60, "h": 3600}
MIB = 2**20


@dataclass(frozen=True)
class UnitSample:
    active: str
    restarts: int
    current: int | None
    swap_current: int | None
    peak: int | None
    swap_peak: int | None
    anon: int | None
    file: int | None
    oom_kills: int


@dataclass(frozen=True)
class SystemSample:
    mem_available: int | None
    swap_free: int | None
    load1: float
    pswpin: int
    pswpout: int
    pgmajfault: int
    pressure_full10: float | None
    zram_used: int | None
    web_private_dirty: int | None
    web_swap: int | None
    top: str


@dataclass(frozen=True)
class Sample:
    at: float
    boot_id: str
    units: dict[str, UnitSample]
    system: SystemSample


def parse_duration(text: str) -> int:
    """Return the seconds in `10m`, `30s`, `1h` or a bare number of seconds."""
    text = text.strip()
    if text.isdigit():
        return int(text)
    unit = text[-1]
    if unit not in UNIT_SECONDS or not text[:-1].isdigit():
        raise ValueError(f"duration must be a number with s, m or h, not {text!r}")
    return int(text[:-1]) * UNIT_SECONDS[unit]


def parse_show(output: str) -> dict[str, str]:
    return dict(line.split("=", 1) for line in output.splitlines() if "=" in line)


def bytes_or_none(value: str | None) -> int | None:
    if value is None or value == "" or value == NOT_SET:
        return None
    return int(value)


def parse_key_values(text: str) -> dict[str, int]:
    """Return the `name value` lines of memory.stat, memory.events or /proc/vmstat."""
    result = {}
    for line in text.splitlines():
        words = line.split()
        if len(words) == 2 and words[1].isdigit():
            result[words[0]] = int(words[1])
    return result


def parse_kb_lines(text: str) -> dict[str, int]:
    """Return the `Name: value kB` lines of /proc/meminfo or smaps_rollup in bytes."""
    result = {}
    for line in text.splitlines():
        words = line.replace(":", " ").split()
        if len(words) >= 3 and words[-1] == "kB" and words[-2].isdigit():
            result[words[0]] = int(words[-2]) * 1024
    return result


def parse_pressure(text: str | None) -> float | None:
    """Return `full avg10` of /proc/pressure/memory, or None where the kernel has no pressure stall information."""
    if not text:
        return None
    for line in text.splitlines():
        words = line.split()
        if words and words[0] == "full":
            fields = dict(word.split("=", 1) for word in words[1:] if "=" in word)
            return float(fields["avg10"])
    return None


def parse_zram(text: str | None) -> int | None:
    """Return mem_used_total, the third field of /sys/block/zram0/mm_stat, or None without zram."""
    if not text:
        return None
    fields = text.split()
    return int(fields[2]) if len(fields) >= 3 else None


def read_sample(host) -> Sample:
    """Read one sample from a testinfra host: both units, the web process, and the system counters."""
    units = {}
    for unit in UNITS:
        show = parse_show(host.run(f"systemctl show -p ActiveState -p NRestarts -p MemoryCurrent -p MemorySwapCurrent -p MemoryPeak -p MemorySwapPeak {unit}").stdout)
        cgroup = f"/sys/fs/cgroup/system.slice/{unit}"
        stat = parse_key_values(host.run(f"cat {cgroup}/memory.stat").stdout)
        events = parse_key_values(host.run(f"cat {cgroup}/memory.events").stdout)
        units[unit] = UnitSample(
            active=show.get("ActiveState", ""),
            restarts=int(show.get("NRestarts", "0")),
            current=bytes_or_none(show.get("MemoryCurrent")),
            swap_current=bytes_or_none(show.get("MemorySwapCurrent")),
            peak=bytes_or_none(show.get("MemoryPeak")),
            swap_peak=bytes_or_none(show.get("MemorySwapPeak")),
            anon=stat.get("anon"),
            file=stat.get("file"),
            oom_kills=events.get("oom_kill", 0),
        )
    meminfo = parse_kb_lines(host.run("cat /proc/meminfo").stdout)
    vmstat = parse_key_values(host.run("cat /proc/vmstat").stdout)
    pressure = host.run("cat /proc/pressure/memory")
    zram = host.run("cat /sys/block/zram0/mm_stat")
    rollup = host.run(f"p=$(pgrep -x {WEB_PROCESS} | head -1); [ -n \"$p\" ] && cat /proc/$p/smaps_rollup")
    web = parse_kb_lines(rollup.stdout) if rollup.rc == 0 else {}
    system = SystemSample(
        mem_available=meminfo.get("MemAvailable"),
        swap_free=meminfo.get("SwapFree"),
        load1=float(host.run("cat /proc/loadavg").stdout.split()[0]),
        pswpin=vmstat.get("pswpin", 0),
        pswpout=vmstat.get("pswpout", 0),
        pgmajfault=vmstat.get("pgmajfault", 0),
        pressure_full10=parse_pressure(pressure.stdout if pressure.rc == 0 else None),
        zram_used=parse_zram(zram.stdout if zram.rc == 0 else None),
        web_private_dirty=web.get("Private_Dirty"),
        web_swap=web.get("Swap"),
        top=host.run("top -bn1 -o %CPU | sed -n '7,12p'").stdout,
    )
    import time

    return Sample(at=time.monotonic(), boot_id=host.run("cat /proc/sys/kernel/random/boot_id").stdout.strip(), units=units, system=system)


def render_table(samples: list[Sample], limits: dict[str, str]) -> str:
    """Return the soak as Markdown: the limits, one row per sample with the swap and fault counters as deltas, the last sample's top lines."""
    first = samples[0]
    lines = [
        f"Boot id {first.boot_id}. Limits: " + ", ".join(f"{unit} memory.max={limit}" for unit, limit in limits.items()) + ".",
        "",
        "| t | scanner RAM+zram | scanner anon/file | kiosk RAM+zram | kiosk anon/file | web private dirty/swap | available | swap free | load | Δswpin | Δswpout | Δmajflt | PSI full10 |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    previous = first
    for sample in samples:
        s, k, sys = sample.units[SCANNER], sample.units[KIOSK], sample.system
        lines.append(
            f"| {sample.at - first.at:.0f} "
            f"| {mb(s.current)}+{mb(s.swap_current)} | {mb(s.anon)}/{mb(s.file)} "
            f"| {mb(k.current)}+{mb(k.swap_current)} | {mb(k.anon)}/{mb(k.file)} "
            f"| {mb(sys.web_private_dirty)}/{mb(sys.web_swap)} "
            f"| {mb(sys.mem_available)} | {mb(sys.swap_free)} | {sys.load1:.1f} "
            f"| {sys.pswpin - previous.system.pswpin} | {sys.pswpout - previous.system.pswpout} | {sys.pgmajfault - previous.system.pgmajfault} "
            f"| {na(sys.pressure_full10)} |"
        )
        previous = sample
    lines += ["", "Top CPU at the last sample:", "", "```", samples[-1].system.top.rstrip(), "```", ""]
    return "\n".join(lines)


def render_summary(samples: list[Sample]) -> str:
    def peak(unit: str) -> int:
        return max((s.units[unit].current or 0) + (s.units[unit].swap_current or 0) for s in samples)

    web = max((s.system.web_private_dirty or 0) for s in samples)
    faults = (samples[-1].system.pgmajfault - samples[0].system.pgmajfault) / max(samples[-1].at - samples[0].at, 1)
    return f"soak: scanner peak {mb(peak(SCANNER))} MB, kiosk peak {mb(peak(KIOSK))} MB RAM+zram, web process private dirty up to {mb(web)} MB, {faults:.1f} major faults/s"


def mb(value: int | None) -> str:
    return "n/a" if value is None else f"{value / MIB:.0f}"


def na(value: float | None) -> str:
    return "n/a" if value is None else f"{value:.2f}"
```

Move `import time` to the top of the module with the other imports.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `uv run --frozen pytest tests/test_sampling.py -q`
Expected: all PASS

- [ ] **Step 5: IDE inspections, then commit**

Call `mcp__idea__get_file_problems` with `errorsOnly: false` for both files and fix what it reports.

```bash
git add tests/sampling.py tests/test_sampling.py
git commit -m "test: parse the memory readings a soak samples"
```

### Task 2: the soak test, its options and marker

**Files:**
- Create: `tests/test_soak.py`
- Modify: `conftest.py`
- Modify: `Makefile`
- Test: `tests/test_conftest.py`

**Interfaces:**
- Consumes: `read_sample`, `render_table`, `render_summary`, `parse_duration`, `UNITS`, `KIOSK` from Task 1.
- Produces: options `--soak-duration` (default `10m`), `--soak-interval` (default `30s`), `--kiosk-conf` (default none);
  marker `soak`; report at `dist/tier2/soak.md` for the VM and `dist/ssh/soak.md` for the board.

- [ ] **Step 1: Write the failing collection tests**

Add to `tests/test_conftest.py`, inside the existing class:

```python
class TestPytestCollectionModifyitems:
    def test_the_soak_collects_with_its_marker_and_options(self):
        result = run_pytest("--collect-only", "-q", "-m", "soak", "--target=ssh", "--target-uri=pi@example", "--soak-duration=1m", "--soak-interval=5s", "tests")

        assert result.returncode == 0, result.stdout + result.stderr
        assert "test_soak.py" in result.stdout

    def test_installed_or_boot_leaves_the_soak_out(self):
        result = run_pytest("--collect-only", "-q", "-m", "installed or boot", "--target=ssh", "--target-uri=pi@example", "tests")

        assert result.returncode == 0, result.stdout + result.stderr
        assert "test_soak.py" not in result.stdout
```

- [ ] **Step 2: Run them to verify they fail**

Run: `uv run --frozen pytest tests/test_conftest.py -q -k soak`
Expected: FAIL, the first because `--soak-duration` is an unknown option.

- [ ] **Step 3: Register the options and the marker**

Replace `conftest.py` with:

```python
"""Tier 2 boots netmon's own device file: the sample rendered for the VM, unless a device directory is given.
The soak and the apt probe register their options and markers here; both are opt-in and skipped on podman."""
import pytest

import booted
import vm_device

BOOTED_ONLY = ("soak", "apt")


def pytest_addoption(parser):
    group = parser.getgroup("netmon")
    group.addoption("--soak-duration", default="10m", help="how long the soak samples, e.g. 10m or 90s")
    group.addoption("--soak-interval", default="30s", help="the time between two samples")
    group.addoption("--kiosk-conf", default=None, help="for --target=vm: a kiosk.conf to write before the soak, for an A/B")
    group.addoption("--apt-timeout", default=300, type=int, help="seconds apt may take next to the stack")
    group.addoption("--apt-package", default="netmon-display", help="the package the apt probe reinstalls")


def pytest_configure(config):
    config.addinivalue_line("markers", "soak: samples both units' memory for minutes on a booted VM or device (vm, ssh), opt-in")
    config.addinivalue_line("markers", "apt: runs apt next to the live stack on a booted VM or device (vm, ssh), opt-in")
    if config.getoption("--target") == "vm" and not config.getoption("--device"):
        config.option.device = str(vm_device.write())


# After the -m deselection, so a run without the display test does not need the browser.
@pytest.hookimpl(trylast=True)
def pytest_collection_modifyitems(config, items):
    if config.getoption("--target") == "podman":
        for item in items:
            if any(marker in item.keywords for marker in BOOTED_ONLY):
                item.add_marker(pytest.mark.skip(reason="needs a booted system"))
        return
    if any(item.path.name == "test_display.py" for item in items) and not booted.webkit_installed():
        raise pytest.UsageError("Playwright's WebKit is not installed; run `make browser`")
```

- [ ] **Step 4: Write the soak test**

```python
import shlex
import time
from pathlib import Path

import pytest

from sampling import KIOSK, UNITS, parse_duration, read_sample, render_summary, render_table

pytestmark = pytest.mark.soak


class TestSoak:
    def test_both_units_hold_for_the_duration(self, host, request, capfd, report, kiosk_variant):
        duration = parse_duration(request.config.getoption("--soak-duration"))
        interval = parse_duration(request.config.getoption("--soak-interval"))
        kiosk_expected = host.file("/dev/dri").exists
        limits = {unit: host.run(f"cat /sys/fs/cgroup/system.slice/{unit}/memory.max").stdout.strip() or "missing" for unit in UNITS}

        samples = [read_sample(host)]
        deadline = time.monotonic() + duration
        while time.monotonic() < deadline:
            time.sleep(interval)
            samples.append(read_sample(host))
        report.write_text(render_table(samples, limits))

        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        with capfd.disabled():
            reporter.ensure_newline()
            reporter.write_line(f"{render_summary(samples)}; table in {report}")
        first, last = samples[0], samples[-1]
        assert last.boot_id == first.boot_id
        for unit in UNITS:
            if unit == KIOSK and not kiosk_expected:
                continue
            assert last.units[unit].active == "active", unit
            assert last.units[unit].restarts == first.units[unit].restarts, unit
            assert last.units[unit].oom_kills == first.units[unit].oom_kills, unit


@pytest.fixture(scope="module")
def report(request) -> Path:
    target = request.config.getoption("--target")
    path = Path.cwd() / "dist" / ("tier2" if target == "vm" else target) / "soak.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


@pytest.fixture(scope="module")
def kiosk_variant(host, request) -> str | None:
    path = request.config.getoption("--kiosk-conf")
    if path is None:
        return None
    if request.config.getoption("--target") == "ssh":
        pytest.fail("--kiosk-conf rewrites the target's kiosk configuration; use it in the VM only")
    content = Path(path).read_text()
    host.check_output(f"printf '%s' {shlex.quote(content)} > /etc/pihero/kiosk.conf")
    host.check_output("systemctl restart pihero-kiosk.service")
    for _ in range(45):
        if "Loaded successfully" in host.run("journalctl -u pihero-kiosk -b --no-pager -o cat").stdout:
            break
        time.sleep(2)
    return path
```

- [ ] **Step 5: Add the make target**

In `Makefile`, add `soak` to `.PHONY` and after `test-tier2`:

```make
soak: ## sample both units for ten minutes: TARGET=pi@host for the board, else the VM; KIOSK_CONF=file for an A/B in the VM
	@$(UV) pytest -m soak $(if $(TARGET),--target=ssh --target-uri=$(TARGET),--target=vm --qemu-accel=$(QEMU_ACCEL)) $(if $(KIOSK_CONF),--kiosk-conf=$(KIOSK_CONF)) $(SOAK_ARGS)
```

- [ ] **Step 6: Run the collection tests to verify they pass**

Run: `uv run --frozen pytest tests/test_conftest.py tests/test_sampling.py -q`
Expected: all PASS

- [ ] **Step 7: IDE inspections, then commit**

```bash
git add conftest.py tests/test_soak.py tests/test_conftest.py Makefile
git commit -m "test: soak both units' memory on a booted target"
```

### Task 3: the apt probe

**Files:**
- Create: `tests/aptprobe.py`
- Create: `tests/test_apt.py`
- Test: `tests/test_aptprobe.py`
- Modify: `tests/test_conftest.py`, `Makefile`

**Interfaces:**
- Consumes: `read_sample`, `parse_show`, `UNITS`, `UnitSample`, `Sample` from Task 1; options from Task 2.
- Produces: `HOOK`, `UNIT`, `command(package) -> str`, `classify(...) -> str`; marker `apt`; the make target
  `apt-probe`.

- [ ] **Step 1: Write the failing tests**

`tests/test_aptprobe.py`:

```python
import pytest

from aptprobe import HOOK, UNIT, classify, command
from sampling import KIOSK, SCANNER, UnitSample

pytestmark = pytest.mark.tier0


class TestCommand:
    def test_runs_update_and_the_reinstall_in_an_accounted_transient_unit(self):
        result = command("netmon-display")

        assert result.startswith(f"systemd-run --unit={UNIT} ")
        assert "-p MemoryAccounting=yes" in result and "-p RemainAfterExit=yes" in result
        assert "apt-get update && " in result and "apt-get install -y --reinstall netmon-display" in result


class TestClassify:
    def test_ok_when_apt_exited_zero_and_nothing_else_changed(self):
        assert classify("exited", "0", "boot-1", "boot-1", units(), units()) == "ok"

    def test_a_different_boot_id_is_a_reset(self):
        assert classify("exited", "0", "boot-1", "boot-2", units(), units()) == "the target rebooted during the run"

    def test_an_unreachable_target_is_reported_as_such(self):
        assert classify("", "", "boot-1", "", units(), units()) == "the target is unreachable after the run"

    def test_a_unit_still_running_apt_is_a_timeout(self):
        assert classify("running", "", "boot-1", "boot-1", units(), units()).startswith("apt did not finish")

    def test_apts_exit_status_is_named(self):
        assert classify("exited", "100", "boot-1", "boot-1", units(), units()) == "apt exited with 100"

    def test_a_restarted_or_stopped_unit_is_named(self):
        assert classify("exited", "0", "boot-1", "boot-1", units(), units(kiosk_restarts=1)) == f"{KIOSK} restarted during the run"
        assert classify("exited", "0", "boot-1", "boot-1", units(), units(scanner_active="inactive")) == f"{SCANNER} is inactive after the run"


def units(scanner_active: str = "active", kiosk_restarts: int = 0) -> dict[str, UnitSample]:
    def unit(active: str, restarts: int) -> UnitSample:
        return UnitSample(active=active, restarts=restarts, current=1, swap_current=1, peak=1, swap_peak=1, anon=1, file=1, oom_kills=0)

    return {SCANNER: unit(scanner_active, 0), KIOSK: unit("active", kiosk_restarts)}
```

Add to `tests/test_conftest.py`, inside the existing class:

```python
class TestPytestCollectionModifyitems:
    def test_the_apt_probe_collects_only_with_its_marker(self):
        selected = run_pytest("--collect-only", "-q", "-m", "apt", "--target=ssh", "--target-uri=pi@example", "--apt-timeout=60", "tests")
        default = run_pytest("--collect-only", "-q", "-m", "installed or boot", "--target=ssh", "--target-uri=pi@example", "tests")

        assert selected.returncode == 0 and "test_apt.py" in selected.stdout, selected.stdout + selected.stderr
        assert "test_apt.py" not in default.stdout
```

- [ ] **Step 2: Run them to verify they fail**

Run: `uv run --frozen pytest tests/test_aptprobe.py tests/test_conftest.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'aptprobe'` and a missing `test_apt.py`.

- [ ] **Step 3: Write the module**

`tests/aptprobe.py`:

```python
"""The apt probe's pure parts: the transient unit's command line and the verdict over what the run changed."""
from sampling import UnitSample

HOOK = "/etc/apt/apt.conf.d/52netmon-dpkg"
UNIT = "netmon-apt-probe"
TERMINAL = {"exited", "failed", "dead"}


def command(package: str) -> str:
    """Return the systemd-run line that runs the update and reinstalls the package as an accounted unit that stays loaded after exit."""
    apt = f"apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --reinstall {package}"
    return f"systemd-run --unit={UNIT} --quiet -p MemoryAccounting=yes -p RemainAfterExit=yes sh -c '{apt}'"


def classify(sub_state: str, exit_status: str, boot_before: str, boot_after: str, before: dict[str, UnitSample], after: dict[str, UnitSample]) -> str:
    """Return `ok`, or one sentence naming what went wrong: a reset, an unreachable target, a timeout, apt's exit status, a unit that restarted or stopped."""
    if not boot_after:
        return "the target is unreachable after the run"
    if boot_after != boot_before:
        return "the target rebooted during the run"
    if sub_state not in TERMINAL:
        return f"apt did not finish within the timeout (unit state {sub_state!r})"
    if exit_status != "0":
        return f"apt exited with {exit_status}"
    for unit, was in before.items():
        now = after[unit]
        if was.active == "active" and now.active != "active":
            return f"{unit} is {now.active} after the run"
        if now.restarts != was.restarts:
            return f"{unit} restarted during the run"
    return "ok"
```

- [ ] **Step 4: Write the probe**

`tests/test_apt.py`:

```python
import time
from pathlib import Path

import pytest

from aptprobe import HOOK, TERMINAL, UNIT, classify, command
from sampling import parse_show, read_sample, render_table

pytestmark = pytest.mark.apt
SAMPLE_INTERVAL = 10


class TestAptNextToTheStack:
    def test_update_and_reinstall_complete_with_both_units_running(self, host, request, capfd, report):
        if host.file(HOOK).exists:
            pytest.fail(f"{HOOK} exists on the target and would stop the units around dpkg; move it aside for the probe")
        timeout = request.config.getoption("--apt-timeout")
        package = request.config.getoption("--apt-package")
        host.run(f"systemctl stop {UNIT}; systemctl reset-failed {UNIT}")
        before = read_sample(host)

        started = time.monotonic()
        host.check_output(command(package))
        samples = [before]
        show = {}
        while time.monotonic() - started < timeout:
            time.sleep(SAMPLE_INTERVAL)
            samples.append(read_sample(host))
            show = parse_show(host.run(f"systemctl show -p SubState -p ExecMainStatus -p MemoryPeak -p Result {UNIT}").stdout)
            if show.get("SubState") in TERMINAL:
                break
        elapsed = time.monotonic() - started
        after = read_sample(host)
        host.run(f"systemctl stop {UNIT}; systemctl reset-failed {UNIT}")
        outcome = classify(show.get("SubState", ""), show.get("ExecMainStatus", ""), before.boot_id, after.boot_id, before.units, after.units)

        faults = after.system.pgmajfault - before.system.pgmajfault
        summary = f"apt probe: {outcome}; {elapsed:.0f} s, apt MemoryPeak={show.get('MemoryPeak', 'n/a')}, {faults} major faults, PSI full10 at the end {after.system.pressure_full10}"
        report.write_text(summary + "\n\n" + render_table(samples + [after], {}))
        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        with capfd.disabled():
            reporter.ensure_newline()
            reporter.write_line(f"{summary}; table in {report}")
        assert outcome == "ok", summary


@pytest.fixture(scope="module")
def report(request) -> Path:
    target = request.config.getoption("--target")
    path = Path.cwd() / "dist" / ("tier2" if target == "vm" else target) / "apt-probe.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path
```

- [ ] **Step 5: Add the make target**

In `Makefile`, add `apt-probe` to `.PHONY` and after `soak`:

```make
apt-probe: ## apt update and a reinstall next to the live stack, with apt's peak and timing: TARGET=pi@host for the board, else the VM
	@$(UV) pytest -m apt $(if $(TARGET),--target=ssh --target-uri=$(TARGET),--target=vm --qemu-accel=$(QEMU_ACCEL)) $(APT_ARGS)
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `uv run --frozen pytest tests/test_aptprobe.py tests/test_conftest.py tests/test_sampling.py -q`
Expected: all PASS

- [ ] **Step 7: IDE inspections, then commit**

```bash
git add tests/aptprobe.py tests/test_aptprobe.py tests/test_apt.py tests/test_conftest.py Makefile
git commit -m "test: probe apt next to the live stack"
```

### Task 4: the first numbers

**Files:**
- Modify: `docs/superpowers/specs/2026-10-02-footprint-design.md` (the Numbers section)
- Modify: `README.md` (the build-and-test block)

- [ ] **Step 1: Document the targets**

In `README.md`'s build-and-test code block, after the `make deploy` line:

```shell
make soak TARGET=pi@netmon.local                    # ten minutes of memory samples of both units: dist/ssh/soak.md (the VM without TARGET)
make apt-probe TARGET=pi@netmon.local               # apt update and a reinstall next to the live stack, timed, with apt's peak
```

- [ ] **Step 2: Soak and probe the VM**

Run: `make gradle && make soak SOAK_ARGS=--soak-duration=5m`
Expected: PASS, a summary line, `dist/tier2/soak.md`. Copy the table's last row and the summary into the spec's Numbers
section under a heading `VM, 1 GB, no swap`.

Run: `make apt-probe`
Expected: PASS with `apt probe: ok`, `dist/tier2/apt-probe.md`. Copy apt's `MemoryPeak` and the wall time into Numbers
as apt's peak.

- [ ] **Step 3: Baseline the board**

The user has unlocked the KeePassXC entry; `ssh-add -l` lists the board's key. Run: `make soak TARGET=pi@netmon.local`
Expected: PASS, `dist/ssh/soak.md`. Copy the table into the spec's Numbers section as the baseline table, and compute
the budget as written there: RAM minus everything else minus apt's peak minus 40 MB, with everything else as used memory
minus the two units' RAM+zram, in the last row.

- [ ] **Step 4: Commit and open the pull request**

```bash
git add README.md docs/superpowers/specs/2026-10-02-footprint-design.md
git commit -m "docs: record the first soak and the budget"
```

Ask the user before pushing; then `git push -u origin test/soak-and-apt-probe` and `gh pr create` with the title `test:
soak and apt probe for the footprint work`.

## Phase 2: the scanner on the JVM

Branch `perf/scanner-slim-classpath` from `origin/main` after Phase 1 merged.

### Task 5: nmap's XML parsed in-process

**Files:**
- Create: `src/jvmMain/kotlin/com/bkahlert/netmon/nmap/NmapXml.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/nmap/NmapNetworkScanner.kt`
- Delete: `src/jvmMain/kotlin/com/bkahlert/netmon/nmap/NmapOutput.kt`, `src/jvmMain/resources/xml2json.py`,
  `src/jvmTest/kotlin/com/bkahlert/netmon/nmap/NmapOutputTest.kt`, `packages/netmon-scanner/tests/test_xml2json.py`
- Modify: `packages/netmon-scanner/nfpm.yaml`, `packages/netmon-scanner/tests/test_installed.py`
- Test: `src/jvmTest/kotlin/com/bkahlert/netmon/nmap/NmapXmlTest.kt`

**Interfaces:**
- Produces: `object NmapXml { fun parse(xml: String): List<Host> }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NmapXmlTest {

    @Test
    fun hosts_with_ip_name_status_and_vendor() {
        val result = NmapXml.parse(nmapRun(UP_WITH_NAME, UP_WITHOUT_NAME, LOCALHOST))

        result.shouldContainExactly(
            Host(ip = IP.of("192.168.42.180"), name = "foo.bar", status = Status.UP, vendor = "Raspberry Pi Trading"),
            Host(ip = IP.of("192.168.42.190"), name = null, status = Status.UP, vendor = "Raspberry Pi Trading"),
            Host(ip = IP.of("192.168.42.33"), name = null, status = Status.UP, vendor = null),
        )
    }

    @Test
    fun a_host_without_an_ip_address_is_left_out() {
        val result = NmapXml.parse(nmapRun(MAC_ONLY, LOCALHOST))

        result shouldHaveSize 1
    }

    @Test
    fun the_first_hostname_wins() {
        val result = NmapXml.parse(nmapRun(TWO_NAMES))

        result.single().name shouldBe "first.local"
    }

    @Test
    fun ipv6_and_down_hosts() {
        val result = NmapXml.parse(nmapRun(IPV6, DOWN))

        result should {
            it[0].ip shouldBe IP.of("fe80::1")
            it[1].status shouldBe Status.DOWN
        }
    }

    @Test
    fun an_empty_run_has_no_hosts() {
        NmapXml.parse(nmapRun()).shouldBeEmpty()
    }

    @Test
    fun the_doctype_is_not_resolved() {
        val xml = nmapRun(LOCALHOST).replace("<!DOCTYPE nmaprun>", "<!DOCTYPE nmaprun SYSTEM \"file:///nonexistent/nmap.dtd\">")

        NmapXml.parse(xml) shouldHaveSize 1
    }
}

private fun nmapRun(vararg hosts: String): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE nmaprun>
    <nmaprun scanner="nmap" args="nmap -sn -oX - 192.168.42.0/24" start="1691000916" version="7.94" xmloutputversion="1.05">
    <verbose level="0"/>
    <debugging level="0"/>
    ${hosts.joinToString("\n")}
    <runstats><finished time="1691000919" elapsed="3.37" exit="success"/><hosts up="18" down="238" total="256"/>
    </runstats>
    </nmaprun>
""".trimIndent()

private val UP_WITH_NAME = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.180" addrtype="ipv4"/>
    <address addr="DC:A6:32:A5:BA:B6" addrtype="mac" vendor="Raspberry Pi Trading"/>
    <hostnames>
    <hostname name="foo.bar" type="PTR"/>
    </hostnames>
    <times srtt="6532" rttvar="6532" to="100000"/>
    </host>
""".trimIndent()

private val UP_WITHOUT_NAME = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.190" addrtype="ipv4"/>
    <address addr="E4:5F:01:34:81:39" addrtype="mac" vendor="Raspberry Pi Trading"/>
    <hostnames>
    </hostnames>
    </host>
""".trimIndent()

private val LOCALHOST = """
    <host><status state="up" reason="localhost-response" reason_ttl="0"/>
    <address addr="192.168.42.33" addrtype="ipv4"/>
    <hostnames>
    </hostnames>
    </host>
""".trimIndent()

private val MAC_ONLY = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="00:11:22:33:44:55" addrtype="mac"/>
    </host>
""".trimIndent()

private val TWO_NAMES = """
    <host><status state="up" reason="arp-response" reason_ttl="0"/>
    <address addr="192.168.42.7" addrtype="ipv4"/>
    <hostnames>
    <hostname name="first.local" type="PTR"/>
    <hostname name="second.local" type="user"/>
    </hostnames>
    </host>
""".trimIndent()

private val IPV6 = """
    <host><status state="up" reason="nd-response" reason_ttl="0"/>
    <address addr="fe80::1" addrtype="ipv6"/>
    </host>
""".trimIndent()

private val DOWN = """
    <host><status state="down" reason="no-response" reason_ttl="0"/>
    <address addr="192.168.42.8" addrtype="ipv4"/>
    </host>
""".trimIndent()
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.nmap.NmapXmlTest' -PunitOnly`
Expected: compilation FAIL, `Unresolved reference: NmapXml`

- [ ] **Step 3: Write the parser**

```kotlin
package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** nmap's XML output (`-oX`) read into [Host] instances. */
object NmapXml {

    private val factory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    }

    /**
     * Returns the hosts of the nmap run in [xml], in document order.
     *
     * A host without an IPv4 or IPv6 address is left out. The name is the first `hostname` element's name and the
     * vendor the MAC address's `vendor` attribute; both are `null` when absent.
     */
    fun parse(xml: String): List<Host> {
        val reader = factory.createXMLStreamReader(xml.reader())
        try {
            return buildList {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "host") {
                        reader.readHost()?.let(::add)
                    }
                }
            }
        } finally {
            reader.close()
        }
    }

    private fun XMLStreamReader.readHost(): Host? {
        var state: String? = null
        var address: String? = null
        var vendor: String? = null
        var name: String? = null
        var depth = 1
        while (depth > 0 && hasNext()) {
            when (next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    when (localName) {
                        "status" -> state = getAttributeValue(null, "state")
                        "address" -> when (getAttributeValue(null, "addrtype")) {
                            "ipv4", "ipv6" -> if (address == null) address = getAttributeValue(null, "addr")
                            "mac" -> vendor = getAttributeValue(null, "vendor")
                        }
                        "hostname" -> if (name == null) name = getAttributeValue(null, "name")
                    }
                }
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
        return address?.let { Host(ip = IP.of(it), name = name, status = state?.let(Status::of), vendor = vendor) }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.nmap.NmapXmlTest' -PunitOnly`
Expected: PASS

- [ ] **Step 5: Wire it in and delete the converter**

In `NmapNetworkScanner.scan`, replace

```kotlin
        val json = XmlToJsonConverter.convert(xml)
        val hosts = JsonFormat.decodeFromString<NmapOutput>(json).nmapRun.hosts
```

with

```kotlin
        val hosts = NmapXml.parse(xml)
```

Delete the `XmlToJsonConverter` object at the bottom of the file and the now unused imports (`URL`, `createTempFile`,
`deleteIfExists`, `readText`, `writeBytes`, `writeText`, `JsonFormat`). Delete `NmapOutput.kt`, `NmapOutputTest.kt`,
`src/jvmMain/resources/xml2json.py` and `packages/netmon-scanner/tests/test_xml2json.py`.

In `packages/netmon-scanner/nfpm.yaml`, remove the `- python3` line. In
`packages/netmon-scanner/tests/test_installed.py`, `test_pulls_in_the_runtime` iterates `("nmap", "mosquitto",
"default-jre-headless")`.

- [ ] **Step 6: Run the JVM unit tests and tier 0**

Run: `make test-jvm && make test-tier0`
Expected: PASS

- [ ] **Step 7: IDE inspections, then commit**

```bash
git add -A src/jvmMain src/jvmTest packages/netmon-scanner
git commit -m "perf(scanner): parse nmap's xml in-process instead of through python"
```

### Task 6: Paho instead of HiveMQ

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/mqtt/MqttPublisher.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/Application.kt` (the "connected" log line)
- Modify: `packages/netmon-scanner/root/usr/lib/systemd/system/netmon-scanner.service`
- Test: `src/jvmTest/kotlin/com/bkahlert/netmon/mqtt/MqttPublisherTest.kt` (pure),
  `src/jvmTest/kotlin/com/bkahlert/netmon/mqtt/MqttPublisherIntegrationTest.kt` (container)

**Interfaces:**
- Produces: `MqttPublisher<T>(host, port, path, stringFormat, serializer, identifier)` as before, plus `val url:
  String`; `toString()` reads `MqttPublisher(url=tcp://host:1883, connected=true)`.

- [ ] **Step 1: Write the failing tests**

Replace `MqttPublisherTest.kt`:

```kotlin
package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.Event
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MqttPublisherTest {

    @Test
    fun url_is_websocket_for_the_brokers_websocket_ports_and_tcp_otherwise() {
        forAll(
            row(1883, null, "tcp://broker.local:1883"),
            row(8080, null, "ws://broker.local:8080"),
            row(8081, "mqtt", "ws://broker.local:8081/mqtt"),
        ) { port, path, expected ->
            MqttPublisher.url("broker.local", port, path) shouldBe expected
        }
    }
}
```

Create `MqttPublisherIntegrationTest.kt`:

```kotlin
package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.DOWN
import com.bkahlert.netmon.Event
import com.bkahlert.netmon.serialization.JsonFormat
import io.kotest.matchers.shouldBe
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MqttPublisherIntegrationTest {

    @Test
    fun publishes_a_retained_event_over_tcp() {
        val publisher = MqttPublisher(host = broker.host, port = broker.firstMappedPort, stringFormat = JsonFormat, serializer = Event.serializer())

        val result = publisher.publish("test", Event.DOWN)

        result shouldBe true
        publisher.toString() shouldBe "MqttPublisher(url=tcp://${broker.host}:${broker.firstMappedPort}, connected=true)"
    }

    private val broker: GenericContainer<*> = GenericContainer<Nothing>(DockerImageName.parse("eclipse-mosquitto:1.5")).withExposedPorts(1883)

    @BeforeTest
    fun setUp() {
        broker.start()
    }

    @AfterTest
    fun tearDown() {
        broker.stop()
    }
}
```

`Event.DOWN` is the fixture `commonTest` defines in `EventTest.kt`; `jvmTest` sees `commonTest`.

- [ ] **Step 2: Run the pure test to verify it fails**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.mqtt.MqttPublisherTest' -PunitOnly`
Expected: compilation FAIL, `Unresolved reference: url`

- [ ] **Step 3: Swap the dependency**

In `build.gradle.kts`, `jvmMain` dependencies: delete the three HiveMQ lines and add

```kotlin
                implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5") { because("publish scans over MQTT 3; no transitive dependencies") }
```

In the `jvmTest` filter for `-PunitOnly`, delete the `excludeTestsMatching("*MqttPublisherTest")` line and its comment;
the integration test is excluded by `*IntegrationTest`.

Run: `./gradlew -q dependencyInsight --configuration jvmRuntimeClasspath --dependency org.eclipse.paho`
Expected: one artifact, no dependencies of its own. Then `./gradlew -q dependencies --configuration jvmRuntimeClasspath
| grep -c netty` prints `0`.

- [ ] **Step 4: Rewrite the publisher**

```kotlin
package com.bkahlert.netmon.mqtt

import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.serialization.JsonFormat
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

/** Publisher based on the [Eclipse Paho MQTT 3 client](https://github.com/eclipse-paho/paho.mqtt.java), QoS 1, retained. */
class MqttPublisher<T>(
    val host: String,
    val port: Int,
    val path: String? = null,
    val stringFormat: StringFormat = JsonFormat,
    val serializer: SerializationStrategy<T>,
    identifier: String? = null,
) : Publisher<T> {
    private val logger by SLF4J

    val url: String = url(host, port, path)

    private val client: MqttClient = MqttClient(url, identifier ?: UUID.randomUUID().toString(), MemoryPersistence()).also {
        logger.info("Connecting to {}", url)
        it.connect(MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
        })
    }

    override fun publish(topic: String, event: T): Boolean {
        val payload = stringFormat.encodeToString(serializer, event).encodeToByteArray()
        logger.debug("Publishing message ({} bytes) to {}", payload.size, topic)
        return try {
            client.publish(topic, MqttMessage(payload).apply { qos = 1; isRetained = true })
            logger.info("Published message ({} bytes) to {}", payload.size, topic)
            true
        } catch (e: MqttException) {
            logger.error("Error publishing to {}", topic, e)
            false
        }
    }

    override fun toString(): String = "${this::class.simpleName}(url=$url, connected=${client.isConnected})"

    companion object {
        /** Returns the broker URI: `ws://` for the broker's websocket ports 8080 and 8081, `tcp://` otherwise, [path] appended when given. */
        fun url(host: String, port: Int, path: String?): String =
            (if (port == 8080 || port == 8081) "ws" else "tcp") + "://$host:$port" + path?.let { "/$it" }.orEmpty()
    }
}
```

In `Application.start`, the publisher log line becomes `logger.info("{} connected", it)` and the `import
net.logstash.logback.argument.StructuredArguments.v` stays until Task 7 removes it.

In the unit, `Environment=BROKER_HOST=localhost BROKER_PORT=8080` becomes `Environment=BROKER_HOST=127.0.0.1
BROKER_PORT=1883`, since Mosquitto's plain listener binds `127.0.0.1` only.

- [ ] **Step 5: Run the tests**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.mqtt.*' ` (Docker running; the integration test starts Mosquitto)
Expected: PASS. Then `make test-jvm && make test-tier0`: PASS.

- [ ] **Step 6: IDE inspections, then commit**

```bash
git add build.gradle.kts src/jvmMain src/jvmTest packages/netmon-scanner/root
git commit -m "perf(scanner): publish with paho over the broker's plain listener"
```

### Task 7: slf4j-simple instead of logback

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/jvmMain/kotlin/com/bkahlert/netmon/logging/LogLevel.kt`,
  `src/jvmMain/kotlin/com/bkahlert/netmon/logging/SimpleLogger.kt`, `src/jvmMain/resources/simplelogger.properties`
- Delete: `src/jvmMain/kotlin/com/bkahlert/netmon/logging/Logback.kt`, `src/jvmMain/resources/logback.xml`,
  `src/jvmTest/resources/logback-integration-test.xml`
- Modify: `Verbosity.kt`, `Debug.kt`, `LoggingSettings.kt`, every file importing `net.logstash` (`Caching.kt`,
  `Application.kt`, `AppleHostEnricher.kt`, `HostEnricher.kt`, `JmDNSServiceInfoCache.kt`, `MqttPublisher.kt`,
  `SystemInterfaceAddressResolver.kt`, `NmapMacPrefixesProvisioner.kt`, `NmapNetworkScanner.kt`, `ScanResult.kt`,
  `SlicedApplication.kt`)
- Modify tests: `LoggingSettingsTest.kt`, `VerbosityTest.kt`, `DebugTest.kt`, `AbstractIntegrationTest.kt`,
  `src/jvmTest/kotlin/com/bkahlert/kommons/config/SettingsTest.jvm.kt`

**Interfaces:**
- Produces: `enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, OFF }`; `object SimpleLogger { const val ROOT =
  "root"; fun configure(levels: Map<String, LogLevel>); fun level(name: String): LogLevel? }`; `Verbosity.levels:
  Map<String, LogLevel>`; `Debug.apply(levels: Map<String, LogLevel>): Map<String, LogLevel>`; `LogMessage.parse(line:
  String): LogMessage?`.

- [ ] **Step 1: Rewrite the failing tests**

`LoggingSettingsTest.kt`:

```kotlin
package com.bkahlert.netmon.logging

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LoggingSettingsTest {

    @Test
    fun apply_sets_the_simple_loggers_levels_from_verbosity_and_debug() {
        System.setProperty("debug", "*.netmon*,-*mdns*")

        LoggingSettings.apply("-v")

        forAll(
            row("com.bkahlert.netmon.net", LogLevel.DEBUG),
            row("javax.jmdns.impl.DNSIncoming", LogLevel.OFF),
            row("com.bkahlert.netmon.mdns.JmDNSServiceInfoCache", LogLevel.OFF),
            row(SimpleLogger.ROOT, LogLevel.INFO),
        ) { logger, level ->
            SimpleLogger.level(logger) shouldBe level
        }
    }
}
```

`VerbosityTest.level` becomes:

```kotlin
    @Test
    fun level() {
        forAll(
            row(Verbosity.ERRORS_AND_WARNINGS, LogLevel.WARN),
            row(Verbosity.VERBOSE, LogLevel.INFO),
            row(Verbosity.VERY_VERBOSE, LogLevel.INFO),
            row(Verbosity.EXTREMELY_VERBOSE, LogLevel.DEBUG),
        ) { verbosity, expected ->
            SimpleLogger.configure(verbosity.levels)
            SimpleLogger.level(SimpleLogger.ROOT) shouldBe expected
        }
    }
```

Its `ch.qos.logback.classic.Level` import goes. In `DebugTest.apply`, `Level.INFO`, `Level.DEBUG`, `Level.OFF` become
`LogLevel.INFO`, `LogLevel.DEBUG`, `LogLevel.OFF` and the import changes.

Add to a new test file `src/jvmTest/kotlin/com/bkahlert/netmon/LogMessageTest.kt`:

```kotlin
package com.bkahlert.netmon

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LogMessageTest {

    @Test
    fun parses_the_simple_loggers_bracketed_lines() {
        val result = LogMessage.parse("[INFO] com.bkahlert.netmon.Application - Settings: foo")

        result shouldBe LogMessage(LogMessage.Level.INFO, "Settings: foo")
    }

    @Test
    fun a_continuation_line_is_not_a_message() {
        LogMessage.parse("                      hostname: netmon").shouldBeNull()
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.logging.*' --tests 'com.bkahlert.netmon.LogMessageTest' -PunitOnly`
Expected: compilation FAIL on `LogLevel`, `SimpleLogger`, `LogMessage.parse`

- [ ] **Step 3: Swap the dependencies**

In `build.gradle.kts`, `jvmMain`: delete the `logback-classic` and `logstash-logback-encoder` lines, add

```kotlin
                implementation("org.slf4j:slf4j-simple:2.0.20") { because("logging to the journal without XML or reflection") }
```

- [ ] **Step 4: Write the level, the configuration and the properties**

`LogLevel.kt`:

```kotlin
package com.bkahlert.netmon.logging

/** The levels slf4j-simple accepts, as the level names of its properties in upper case. */
enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, OFF }
```

`SimpleLogger.kt`:

```kotlin
package com.bkahlert.netmon.logging

/**
 * slf4j-simple's levels through its system properties.
 *
 * A level applies to loggers created after it is set; the root level is read once, at the first logger.
 */
object SimpleLogger {

    /** The name standing for the default level of every logger without one of its own. */
    const val ROOT = "root"

    /** Sets each logger's level; [ROOT] sets the default level. */
    fun configure(levels: Map<String, LogLevel>): Unit =
        levels.forEach { (name, level) -> System.setProperty(key(name), level.name.lowercase()) }

    /** Returns the level set for [name], or `null` if none is set. */
    fun level(name: String): LogLevel? = System.getProperty(key(name))?.let { LogLevel.valueOf(it.uppercase()) }

    private fun key(name: String): String =
        if (name == ROOT) "org.slf4j.simpleLogger.defaultLogLevel" else "org.slf4j.simpleLogger.log.$name"
}
```

`src/jvmMain/resources/simplelogger.properties`:

```properties
org.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSS
org.slf4j.simpleLogger.defaultLogLevel=warn
org.slf4j.simpleLogger.levelInBrackets=true
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.showLogName=true
org.slf4j.simpleLogger.showThreadName=true
```

`Verbosity.kt`: replace `ch.qos.logback.classic.Level` with `LogLevel` everywhere (`Level.WARN` becomes `LogLevel.WARN`,
and so on), and in `EXTREMELY_VERBOSE` replace `"io.netty" to Level.INFO` with `"org.eclipse.paho" to LogLevel.INFO`.

`Debug.kt`: the import becomes `LogLevel`; `apply(levels: Map<String, LogLevel>): Map<String, LogLevel>` with
`LogLevel.DEBUG` and `LogLevel.OFF`.

`LoggingSettings.apply`:

```kotlin
    fun apply(vararg args: String) {
        (Verbosity.from(*args).takeIf { it.ordinal > 0 } ?: verbosity)
            .levels
            .let { debug.apply(it) }
            .let { SimpleLogger.configure(it) }
    }
```

Delete `Logback.kt`, `logback.xml`, `logback-integration-test.xml`.

- [ ] **Step 5: Replace the structured arguments**

In every file listed above, delete the `net.logstash.logback.argument.StructuredArguments` imports and change the call
sites: `kv("name", value)` becomes the argument `value` with `name={}` in the message template, `v("name", value)`
becomes `value` with `{}`. For example, `NmapMacPrefixesProvisioner.provisionIn` becomes

```kotlin
    fun provisionIn(directory: Path): Path =
        directory.createDirectories().resolve(NMAP_MAC_PREFIXES_FILENAME)
            .apply { outputStream().buffered().use { data.copyTo(it) } }
            .also { logger.info("Provisioned file={} at path={}", NMAP_MAC_PREFIXES_FILENAME, it) }
```

In `NmapNetworkScanner.scan`, the two log lines become

```kotlin
        logger.info("Scanning network {}", network)
        logger.info("Discovered hosts={} in network={}", hosts, network)
```

`ApplicationIntegrationTest` asserts the line `Provisioned file=nmap-mac-prefixes at path=./nmap/nmap-mac-prefixes`;
keep that wording.

- [ ] **Step 6: Parse the simple logger's lines in the integration tests**

In `AbstractIntegrationTest.kt`, the process gets two system properties so that lines read `[INFO] logger - message`;
they replace `-Dlogback.configurationFile` and `-DLOG_FILE`:

```kotlin
        val process = ProcessBuilder(
            Paths.get(System.getProperty("java.home"), "bin", "java").pathString,
            "-Dorg.slf4j.simpleLogger.showDateTime=false",
            "-Dorg.slf4j.simpleLogger.showThreadName=false",
            "-cp", System.getProperty("java.class.path"),
            checkNotNull(kClass.qualifiedName) { "$kClass has no qualified name" },
            *arguments,
        )
```

The output consumer collects lines into a `Collections.synchronizedList(mutableListOf<String>())` and `runUntilLogged`
returns `lines.mapNotNull(LogMessage::parse)`; the `logFile`, `logbackConfiguration` and `readLogMessages` go.
`LogMessage` becomes:

```kotlin
data class LogMessage(val level: Level, val message: String) {
    enum class Level { TRACE, DEBUG, INFO, WARN, ERROR }

    companion object {
        private val LINE = Regex("""^\[(?<level>TRACE|DEBUG|INFO|WARN|ERROR)] (?<logger>\S+) - (?<message>.*)$""")

        /** Returns the message of a simple-logger line, or `null` for a continuation line. */
        fun parse(line: String): LogMessage? = LINE.matchEntire(line)?.let {
            LogMessage(Level.valueOf(it.groups["level"]!!.value), it.groups["message"]!!.value)
        }
    }
}
```

In `SettingsTest.jvm.kt`, `withTestConfig` loses the `Logback` logger handling and its two imports; it swaps the system
properties only.

- [ ] **Step 7: Run the tests**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.logging.*' --tests 'com.bkahlert.netmon.LogMessageTest'
-PunitOnly`, then `make test-jvm`, then `./gradlew jvmTest --tests 'com.bkahlert.netmon.ApplicationIntegrationTest'`
with Docker running.
Expected: PASS. `./gradlew -q dependencies --configuration jvmRuntimeClasspath | grep -c -E 'jackson|logback'` prints
`0`.

- [ ] **Step 8: IDE inspections, then commit**

```bash
git add -A build.gradle.kts src/jvmMain src/jvmTest
git commit -m "perf(scanner): log through slf4j-simple instead of logback"
```

### Task 8: the process id and the cadence

**Files:**
- Modify: `src/jvmMain/kotlin/com/bkahlert/kommons/Pid.kt`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScannerSettings.kt`

- [ ] **Step 1: Read the pid without reflection**

```kotlin
package com.bkahlert.kommons

@JvmInline
value class Pid(val value: Long) {
    override fun toString(): String = "PID($value)"

    companion object {
        val current: Pid by lazy { Pid(ProcessHandle.current().pid()) }
    }
}
```

Run: `./gradlew jvmTest --tests 'com.bkahlert.kommons.PidTest' -PunitOnly`. Expected: PASS.

```bash
git add src/jvmMain/kotlin/com/bkahlert/kommons/Pid.kt
git commit -m "refactor(scanner): read the pid from processhandle"
```

- [ ] **Step 2: Scan every 30 seconds**

In `ScannerSettings`, `setting(default = 10.seconds)` becomes `setting(default = 30.seconds)`.

Run: `make test-jvm`. Expected: PASS.

```bash
git add src/jvmMain/kotlin/com/bkahlert/netmon/scanner/ScannerSettings.kt
git commit -m "perf(scanner): pause 30 seconds between scans"
```

- [ ] **Step 3: Tiers 1 and 2, then the pull request**

Run: `make gradle && make test-tier1 && make test-tier2`
Expected: PASS; the scanner memory line in the tier 2 output is the JVM baseline after the slimming, noted in the spec's
Numbers.

Ask the user before pushing; then push and `gh pr create` with the title `perf(scanner): slim the classpath for the
native image`.

## Phase 3: the native image

Branch `perf/scanner-native-image` from `origin/main` after Phase 2 merged.

### Task 9: the native-image configuration in the jar and the heap log line

**Files:**
- Create: `src/jvmMain/resources/META-INF/native-image/com.bkahlert.netmon/netmon-scanner/native-image.properties`
- Create: `src/jvmMain/resources/META-INF/native-image/com.bkahlert.netmon/netmon-scanner/reachability-metadata.json`
- Modify: `src/jvmMain/kotlin/com/bkahlert/netmon/Application.kt`
- Test: `src/jvmTest/kotlin/com/bkahlert/netmon/ApplicationIntegrationTest.kt`

- [ ] **Step 1: Assert the heap line in the integration test**

In `ApplicationIntegrationTest.scan_and_publish`, add to the `logMessages should { ... }` block:

```kotlin
            it.forAny { (_, message) -> message.shouldContain("Configuration: ") }
```

A new test class `ApplicationTest` in `src/jvmTest/kotlin/com/bkahlert/netmon/ApplicationTest.kt`:

```kotlin
package com.bkahlert.netmon

import io.kotest.matchers.string.shouldMatch
import kotlin.test.Test

class ApplicationTest {

    @Test
    fun configuration_names_the_max_heap_in_bytes() {
        val result = Application.configuration(hostname = "host", cache = "cache")

        result shouldMatch Regex("(?s).*max heap: \\d+ bytes.*")
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.ApplicationTest' -PunitOnly`
Expected: compilation FAIL, `Unresolved reference: configuration`

- [ ] **Step 3: Extract the configuration text and add the heap**

In `Application`, replace the first `logger.info("Configuration: {}", listOf(...).joinToString(...))` with
`logger.info("Configuration: {}", configuration(hostname, cache.toString()))` and add to the companion:

```kotlin
        /** Returns the configuration block the start logs: the hostname, the cache, and the maximum heap the runtime allows in bytes. */
        fun configuration(hostname: String, cache: String): String = listOf(
            "hostname" to hostname,
            "cache" to cache,
            "max heap" to "${Runtime.getRuntime().maxMemory()} bytes",
        ).joinToString(separator = "") { (key, value) -> "\n${key.padStart(30)}: $value" }
```

- [ ] **Step 4: Write the native-image files**

`native-image.properties`:

```properties
Args = --no-fallback \
       -march=compatibility \
       --install-exit-handlers \
       -R:MaxHeapSize=64m
```

`reachability-metadata.json`:

```json
{
  "reflection": [
    { "type": "sun.net.www.protocol.http.Handler", "methods": [{ "name": "<init>", "parameterTypes": [] }] },
    { "type": "sun.net.www.protocol.https.Handler", "methods": [{ "name": "<init>", "parameterTypes": [] }] }
  ],
  "resources": [
    { "glob": "assets/device-model-codes.json" },
    { "glob": "simplelogger.properties" },
    { "glob": "version.properties" }
  ]
}
```

The two handlers are how current GraalVM enables `http` and `https` URLs; `version.properties` is JmDNS's one resource;
`--install-exit-handlers` makes SIGTERM run the shutdown hooks and exit with 143 as the JVM did, which
`SuccessExitStatus=143` in the unit relies on.

- [ ] **Step 5: Run the tests**

Run: `./gradlew jvmTest --tests 'com.bkahlert.netmon.ApplicationTest' -PunitOnly && make test-jvm`
Expected: PASS

- [ ] **Step 6: IDE inspections, then commit**

```bash
git add src/jvmMain src/jvmTest
git commit -m "build(scanner): configure the native image and log the heap cap"
```

### Task 10: the container build and the Makefile rule

**Files:**
- Create: `packages/netmon-scanner/native/Containerfile`
- Create: `packages/netmon-scanner/native/compile` (executable)
- Modify: `Makefile`

- [ ] **Step 1: Write the Containerfile**

```Dockerfile
# Builds the scanner's native image: GraalVM Community's native-image for linux-aarch64, pinned to the arm64 manifest.
FROM ghcr.io/graalvm/native-image-community:25@sha256:649f9d929e30f8d4c7672b32426674817b687f759c61a5ed721afe73524afba2
# One-shot build image run with `podman run --rm`; there is no long-running service to probe.
HEALTHCHECK NONE
CMD ["bash"]
```

- [ ] **Step 2: Write the build script**

```bash
#!/usr/bin/env bash
# Purpose: Build the scanner's native image from the shadow jar, inside the GraalVM container.
# Usage:   compile [--jar <path>] [--out <path>]
#
# Options:
#   --jar <path>   The shadow jar (default: build/libs/netmon-all.jar).
#   --out <path>   The binary to write (default: build/native/netmon-scanner).
#   -h, --help     Show this help.
#
# Runs inside the image built from the Containerfile next to this script, with the repository mounted at /work.
# The build arguments ride in the jar under META-INF/native-image. The builder's heap is 70 % of the memory the
# container sees; the build output ends with its peak RSS.

set -euo pipefail

usage() { awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "${BASH_SOURCE[0]}"; }
die()   { printf '%s: %s\nSee '\''%s --help'\''\n' "${0##*/}" "$1" "${0##*/}" >&2; exit 2; }

jar=build/libs/netmon-all.jar
out=build/native/netmon-scanner
while (( $# )); do
  case $1 in
    -h|--help) usage; exit 0 ;;
    --jar)     jar=${2?--jar: missing value}; shift 2 ;;
    --jar=*)   jar=${1#*=}; shift ;;
    --out)     out=${2?--out: missing value}; shift 2 ;;
    --out=*)   out=${1#*=}; shift ;;
    -?*)       die "unknown option: $1" ;;
    *)         die "unexpected argument: $1" ;;
  esac
done
[[ -f $jar ]] || die "jar not found: $jar"

memory_mib=$(( $(awk '/^MemTotal/ {print $2}' /proc/meminfo) * 7 / 10 / 1024 ))
mkdir -p "$(dirname "$out")"
native-image -J-Xmx"${memory_mib}m" --parallelism="$(nproc)" -jar "$jar" -o "$out"
```

`chmod +x packages/netmon-scanner/native/compile`.

- [ ] **Step 3: Wire the Makefile**

Replace the `gradle` target and add the file rule:

```make
NATIVE_IMAGE := localhost/netmon-native:$(shell shasum -a 256 packages/netmon-scanner/native/Containerfile | cut -c1-12)

gradle: ## build the scanner jar, its native binary and the web bundle
	./gradlew $(GRADLE_ARGS) shadowJar jsBrowserDistribution
	@$(MAKE) build/native/netmon-scanner

build/native/netmon-scanner: build/libs/netmon-all.jar packages/netmon-scanner/native/Containerfile packages/netmon-scanner/native/compile
	@podman image exists $(NATIVE_IMAGE) || podman build --platform linux/arm64 -t $(NATIVE_IMAGE) -f packages/netmon-scanner/native/Containerfile packages/netmon-scanner/native
	podman run --rm --platform linux/arm64 -v "$(CURDIR):/work" -w /work $(NATIVE_IMAGE) packages/netmon-scanner/native/compile
```

- [ ] **Step 4: Build**

Run: `make gradle`
Expected: the image builds once, `native-image` runs for a few minutes and prints its peak RSS,
`build/native/netmon-scanner` exists and `file build/native/netmon-scanner` says `ELF 64-bit LSB pie executable, ARM
aarch64`. Note the build's peak RSS and duration for the pull request. If the build dies for lack of memory, report the
peak to the user; `podman machine set --memory 6144` is their decision.

Run `make gradle` a second time: the file rule is up to date and no container runs.

- [ ] **Step 5: Tier 0, then commit**

Run: `make test-tier0` (shellcheck covers `compile`).

```bash
git add packages/netmon-scanner/native Makefile
git commit -m "build(scanner): compile the native image in a graalvm container"
```

### Task 11: the package, the unit, the static and the installed tests

**Files:**
- Modify: `packages/netmon-scanner/nfpm.yaml`
- Modify: `packages/netmon-scanner/root/usr/lib/systemd/system/netmon-scanner.service`
- Modify: `tests/test_static.py`
- Modify: `packages/netmon-scanner/tests/test_installed.py`

- [ ] **Step 1: Rewrite the installed tests that change**

In `packages/netmon-scanner/tests/test_installed.py`:

```python
class TestPackage:
    def test_pulls_in_the_runtime(self, host):
        for name in ("nmap", "mosquitto"):
            assert host.package(name).is_installed, name
```

```python
class TestUnit:
    def test_starts_with_the_shipped_heap_cap(self, host):
        """The unit's options reach the binary: a 48 MB cap logs a maximum heap at most that large and well above 40 MB."""
        log = journal_until(host, "max heap: ")
        match = re.search(r"max heap: (?P<bytes>\d+) bytes", log)
        assert match, log
        assert 40 * 2**20 <= int(match["bytes"]) <= 48 * 2**20
```

replacing `test_starts_the_jvm_with_the_shipped_options`; add `import re`. In `test_purge_leaves_nothing_behind`, the
jar path becomes `/usr/lib/netmon/netmon-scanner`. In `test_carries_the_capabilities_and_the_memory_cap`, keep
`MemoryMax=335544320` until Task 13 lowers it.

- [ ] **Step 2: The package**

`packages/netmon-scanner/nfpm.yaml`:

```yaml
name: netmon-scanner
arch: arm64
platform: linux
version: ${VERSION}
section: net
priority: optional
maintainer: Björn Kahlert <bkahlert@users.noreply.github.com>
description: |
  Netmon: scans the networks the device is on and publishes hosts over MQTT.
  A native service around nmap and mDNS, publishing to the local Mosquitto broker, whose websocket
  listener on port 8080 this package configures; the web display (netmon-display) subscribes there.
homepage: https://github.com/bkahlert/netmon
license: MIT
depends:
  - nmap
  - mosquitto
contents:
  - src: root/
    dst: /
    type: tree
  - src: ../../build/native/netmon-scanner
    dst: /usr/lib/netmon/netmon-scanner
    file_info:
      mode: 0755
  - src: conf/mosquitto-netmon.conf
    dst: /etc/mosquitto/conf.d/netmon.conf
    type: config
scripts:
  postinstall: .build/postinst
  preremove: .build/prerm
  postremove: .build/postrm
```

- [ ] **Step 3: The unit**

In the `[Service]` section, replace the `JAVA_TOOL_OPTIONS` line and `ExecStart`:

```ini
Environment=BROKER_HOST=127.0.0.1 BROKER_PORT=1883
Environment=NMAP_DATA_DIR=/var/lib/netmon/nmap XDG_CACHE_HOME=/var/cache/netmon
# Runtime options of the native image, the heap cap first of all; scanner.conf may override them.
Environment=NETMON_SCANNER_OPTIONS=-Xmx48m
EnvironmentFile=-/etc/netmon/scanner.conf
StateDirectory=netmon
CacheDirectory=netmon
WorkingDirectory=/var/lib/netmon
ExecStart=/usr/lib/netmon/netmon-scanner $NETMON_SCANNER_OPTIONS
# The binary answers SIGTERM like the JVM did, by exiting with 143 rather than dying from the signal; without this a stop ends "failed".
SuccessExitStatus=143
```

The nmap capabilities comment stays as it is; the capabilities pass to nmap from the binary as they did from the JVM.

- [ ] **Step 4: The static check's stub**

In `tests/test_static.py`: `STUBBED_COMMANDS = ("/usr/lib/netmon/netmon-scanner",)`.

- [ ] **Step 5: Run tiers 0 and 1**

Run: `make build && make test-tier0 && make test-tier1`
Expected: PASS: the deb installs without a JRE, the unit starts, the journal shows `max heap: ` with a value between 40
and 48 MiB, `connected`, and a stop leaves the unit inactive.

- [ ] **Step 6: Tier 2**

Run: `make test-tier2`
Expected: PASS; the scanner's memory line is the first native number for the spec's Numbers section, and the journal in
the VM shows `Provisioned file=nmap-mac-prefixes`, which proves https.

- [ ] **Step 7: IDE inspections, then commit**

```bash
git add packages/netmon-scanner tests/test_static.py
git commit -m "feat(scanner)!: ship the scanner as a native arm64 binary without a jre"
```

The commit body carries `BREAKING CHANGE: netmon-scanner is arm64 only and no longer depends on default-jre-headless or
python3.`

### Task 12: CI and the documentation

**Files:**
- Modify: `.github/workflows/ci.yml`, `.github/workflows/release.yml`
- Modify: `README.md`, `devices/README.md`

- [ ] **Step 1: CI**

In `ci.yml`, the `tier1` job loses its `strategy` block and the `docker/setup-qemu-action` step, its cache suffix
becomes `cache-suffix: tier1`, and its last step is `- run: make test-tier1`. In `release.yml`, delete the
`docker/setup-qemu-action` step and the line `- run: make test-tier1 PLATFORM=linux/arm/v7`.

- [ ] **Step 2: README**

In the About list, `a JVM-based network scanner` becomes `a network scanner, Kotlin compiled to a native arm64 binary
with GraalVM,`. In the installation section, `JAVA_TOOL_OPTIONS` becomes `NETMON_SCANNER_OPTIONS` and the first sentence
gains `, on a 64-bit Raspberry Pi OS,` after `Pi Hero 2`. In the build block, the `make build` comment becomes `#
Gradle, the native binary in a podman container, then nfpm: dist/*.deb`, and after the block a sentence: `The native
image is built by GraalVM's native-image inside a container
([packages/netmon-scanner/native](packages/netmon-scanner/native)), so podman is needed for make build as it is for tier
1; the binary is rebuilt only when the jar changed.`

In `devices/README.md`, the scanner's overrides sentence lists `NETMON_SCANNER_OPTIONS` (the native image's runtime
options, `-Xmx48m` by default) instead of `JAVA_TOOL_OPTIONS`.

- [ ] **Step 3: Commit and open the pull request**

```bash
git add .github README.md devices/README.md
git commit -m "ci: test arm64 only, the scanner's native image needs no other"
```

Ask the user before pushing; then push and `gh pr create` with the title `feat(scanner)!: the scanner as a native arm64
image`. CI's tier 0 and tier 1 jobs run the container build on the arm runner; note their duration in the pull request.

### Task 13: the board gets the native scanner, and the caps follow the soak

- [ ] **Step 1: Deploy**

Run: `make deploy TARGET=pi@netmon.local`. The board's hook stops both units around dpkg and starts them again.
Expected: `systemctl status netmon-scanner` active, `journalctl -u netmon-scanner -b -o cat | grep -E 'max
heap|connected|completed and published'` shows all three.

Run: `uv run --frozen pytest -m installed --target=ssh --target-uri=pi@netmon.local packages`
Expected: PASS

- [ ] **Step 2: Soak both**

Run: `make soak TARGET=pi@netmon.local` and `make gradle && make soak`
Expected: both PASS. Add the board's and the VM's last rows to the spec's Numbers section under `After the native
scanner`.

- [ ] **Step 3: Set the scanner's cap**

`MemoryMax` in the unit becomes three times the scanner's steady RAM+zram from the board soak, rounded up to a multiple
of 16 MB, and `test_carries_the_capabilities_and_the_memory_cap` asserts that value in bytes.

```bash
git add packages/netmon-scanner docs/superpowers/specs/2026-10-02-footprint-design.md
git commit -m "perf(scanner): cap the native scanner's memory from the soak"
```

Ask before pushing. This commit goes onto a short branch `perf/scanner-memory-cap` and its own pull request.

## Phase 4: the kiosk

Branch `perf/kiosk-knobs-and-calmer-page` from `origin/main` after Phase 3 merged.

### Task 14: the kiosk configuration and the plumbing test

**Files:**
- Modify: `devices/sample/user-data`
- Modify: `tests/booted.py`, `tests/test_boot.py`
- Test: `tests/test_booted.py`

**Interfaces:**
- Produces: `booted.kiosk_conf(text: str) -> dict[str, str]`.

- [ ] **Step 1: Write the failing parser test**

Add to `tests/test_booted.py`:

```python
class TestKioskConf:
    def test_reads_assignments_and_strips_double_quotes(self):
        text = 'URL=http://localhost/?a=1&b=2\nCOG_ARGS="--doc-viewer --web-mem-limit=200"\n# a comment\n\nJSC_useFTLJIT=false\n'

        result = kiosk_conf(text)

        assert result == {"URL": "http://localhost/?a=1&b=2", "COG_ARGS": "--doc-viewer --web-mem-limit=200", "JSC_useFTLJIT": "false"}
```

and `kiosk_conf` to the import from `booted`.

- [ ] **Step 2: Run it to verify it fails**

Run: `uv run --frozen pytest tests/test_booted.py -q -k kiosk`
Expected: FAIL, `ImportError: cannot import name 'kiosk_conf'`

- [ ] **Step 3: Write the parser**

In `tests/booted.py`:

```python
def kiosk_conf(text: str) -> dict[str, str]:
    """Return the assignments of an EnvironmentFile such as /etc/pihero/kiosk.conf, double quotes stripped, comments and blanks skipped."""
    result = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        name, _, value = stripped.partition("=")
        result[name.strip()] = value.strip().strip('"')
    return result
```

- [ ] **Step 4: Write the boot tests**

In `tests/test_boot.py`, inside the existing class:

```python
class TestKiosk:
    def test_runs_cog_with_the_configured_arguments(self, host):
        if not host.file("/dev/dri").exists:
            pytest.skip("no display adapter")
        conf = kiosk_conf(host.file("/etc/pihero/kiosk.conf").content_string)
        pid = host.check_output("pgrep -x cog").split()[0]

        cmdline = host.check_output(f"tr '\\0' '\\n' < /proc/{pid}/cmdline").splitlines()

        assert set(shlex.split(conf["COG_ARGS"])) <= set(cmdline), cmdline

    def test_the_web_process_sees_the_webkit_variables(self, host):
        if not host.file("/dev/dri").exists:
            pytest.skip("no display adapter")
        conf = kiosk_conf(host.file("/etc/pihero/kiosk.conf").content_string)
        expected = {f"{name}={value}" for name, value in conf.items() if name.startswith(("JSC_", "WEBKIT_"))}
        pid = host.check_output("pgrep -x WPEWebProcess").split()[0]

        environ = host.check_output(f"tr '\\0' '\\n' < /proc/{pid}/environ").splitlines()

        assert expected <= set(environ), sorted(environ)
```

with `import shlex` and `kiosk_conf` added to the imports from `booted`.

- [ ] **Step 5: The configuration lines**

In `devices/sample/user-data`, the kiosk entry becomes:

```yaml
  # The kiosk shows the web display; the page takes the broker from the URL. The mode hint is for a panel without EDID.
  # COG_ARGS: the document-viewer cache model; WebKit's own memory limit for the web process, checked every ten seconds,
  # so it frees caches before the kernel swaps it; a web process restart at the kill threshold as the leak guard.
  # JSC_ and WEBKIT_ reach the web process through its environment: the upper JIT tiers off, one Skia painting thread.
  - path: /etc/pihero/kiosk.conf
    content: |
      URL=http://localhost/?broker.host=localhost&broker.port=8080
      COG_PLATFORM_DRM_VIDEO_MODE=800x480
      COG_ARGS="--doc-viewer --web-mem-limit=200 --web-check-interval=10 --web-kill-threshold=0.95 --webprocess-failure=restart"
      JSC_useDFGJIT=false
      JSC_useFTLJIT=false
      WEBKIT_SKIA_CPU_PAINTING_THREADS=1
```

The limit of 200 MiB puts WebKit's strict threshold at 100 MB of private dirty memory, which is the web process's share
of a kiosk budget near 150 MB; Task 19 adjusts it from the A/Bs.

- [ ] **Step 6: Run tier 0 and tier 2**

Run: `uv run --frozen pytest tests/test_booted.py -q && make test-tier0` (the schema check covers the device file), then
`make gradle && make test-tier2`.
Expected: PASS, the two new boot tests included; `dist/tier2/kiosk.png` shows the page as before.

- [ ] **Step 7: IDE inspections, then commit**

```bash
git add devices/sample/user-data tests/booted.py tests/test_booted.py tests/test_boot.py
git commit -m "perf(kiosk): give cog a memory limit and webkit a lighter jit from the device file"
```

### Task 15: auto-reload once a minute

**Files:**
- Modify: `src/jsMain/kotlin/com/bkahlert/kommons/browser/AutoRefreshers.kt`
- Test: `src/jsTest/kotlin/com/bkahlert/kommons/browser/AutoRefresherTest.kt`

**Interfaces:**
- Produces: `AutoRefresher(uri, etag = null, window = kotlinx.browser.window, interval: Duration =
  AutoRefresher.INTERVAL)` with `INTERVAL = 1.minutes`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.bkahlert.kommons.browser

import com.bkahlert.netmon.uri.toUriOrNull
import io.kotest.matchers.collections.shouldContainExactly
import org.w3c.dom.Window
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class AutoRefresherTest {

    @Test
    fun polls_at_the_given_interval() {
        val delays = mutableListOf<Int>()

        AutoRefresher(uri = "http://localhost/netmon.js".toUriOrNull()!!, window = windowRecording(delays), interval = 50.milliseconds)

        delays.shouldContainExactly(50)
    }

    @Test
    fun polls_once_a_minute_by_default() {
        val delays = mutableListOf<Int>()

        AutoRefresher(uri = "http://localhost/netmon.js".toUriOrNull()!!, window = windowRecording(delays))

        delays.shouldContainExactly(60_000)
    }
}

private fun windowRecording(delays: MutableList<Int>): Window {
    val window = js("({})")
    window.setInterval = { _: dynamic, delay: Int -> delays.add(delay); 1 }
    return window.unsafeCast<Window>()
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew jsBrowserTest --tests 'com.bkahlert.kommons.browser.AutoRefresherTest'`
Expected: compilation FAIL, no `interval` parameter

- [ ] **Step 3: Add the parameter**

```kotlin
class AutoRefresher(
    val uri: Uri,
    var etag: String? = null,
    val window: Window = kotlinx.browser.window,
    interval: Duration = INTERVAL,
) {

    init {
        window.setInterval(::refresh, interval.inWholeMilliseconds.toInt())
    }

    /* refresh() as before */

    companion object {
        /** Once a minute: enough for a new deploy to reach the panel, a twelfth of the requests five seconds cost. */
        val INTERVAL = 1.minutes

        /* getEtagOrNull as before */
    }
}
```

with `import kotlin.time.Duration` and `import kotlin.time.Duration.Companion.minutes`, the `seconds` import gone.

- [ ] **Step 4: Run the tests, then commit**

Run: `./gradlew jsBrowserTest --tests 'com.bkahlert.kommons.browser.AutoRefresherTest'`. Expected: PASS.

```bash
git add src/jsMain/kotlin/com/bkahlert/kommons/browser/AutoRefreshers.kt src/jsTest/kotlin/com/bkahlert/kommons/browser/AutoRefresherTest.kt
git commit -m "perf(display): poll for a new bundle once a minute"
```

### Task 16: the radar icons pulse after a scan, then rest

**Files:**
- Create: `src/jsMain/kotlin/com/bkahlert/netmon/ui/pulse.kt`
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/UiSettings.kt`, `src/jsMain/kotlin/com/bkahlert/netmon/ui/network.kt`
- Test: `src/jsTest/kotlin/com/bkahlert/netmon/ui/PulseKtTest.kt`

**Interfaces:**
- Produces: `fun radarClass(sincePublished: Duration, pulseDuration: Duration = UiSettings.SCAN_PULSE_DURATION,
  datedThreshold: Duration = ScanEventSettings.datedThreshold): String`; `UiSettings.SCAN_PULSE_DURATION = 10.seconds`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.bkahlert.netmon.ui

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PulseKtTest {

    @Test
    fun pulses_for_ten_seconds_rests_and_turns_dated_after_two_minutes() {
        forAll(
            row(Duration.ZERO, "animate-variable-color"),
            row((-5).seconds, "animate-variable-color"),
            row(9.seconds, "animate-variable-color"),
            row(10.seconds, ""),
            row(119.seconds, ""),
            row(121.seconds, "text-yellow-500/60"),
        ) { since, expected ->
            radarClass(since) shouldBe expected
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew jsBrowserTest --tests 'com.bkahlert.netmon.ui.PulseKtTest'`
Expected: compilation FAIL, `Unresolved reference: radarClass`

- [ ] **Step 3: Write the function and use it**

`pulse.kt`:

```kotlin
package com.bkahlert.netmon.ui

import com.bkahlert.netmon.ScanEventSettings
import com.bkahlert.netmon.UiSettings
import kotlin.time.Duration

/**
 * Returns the class of the radar icons for a scan published [sincePublished] ago: the animation while younger than
 * [pulseDuration], the dated colour once older than [datedThreshold], no class in between.
 */
fun radarClass(
    sincePublished: Duration,
    pulseDuration: Duration = UiSettings.SCAN_PULSE_DURATION,
    datedThreshold: Duration = ScanEventSettings.datedThreshold,
): String = when {
    sincePublished > datedThreshold -> "text-yellow-500/60"
    sincePublished < pulseDuration -> "animate-variable-color"
    else -> ""
}
```

`UiSettings` gains:

```kotlin
    /** How long the radar icons animate after a scan arrives. */
    val SCAN_PULSE_DURATION: Duration = 10.seconds
```

In `network.kt`'s `meta`, delete `val scanIsDatedFlow = timePassed.map { it > datedThreshold }` and both
`className(scanIsDatedFlow.map { ... })` become `className(timePassed.map { radarClass(it, datedThreshold =
datedThreshold) })`.

- [ ] **Step 4: Run the tests, then commit**

Run: `./gradlew jsBrowserTest`. Expected: PASS.

```bash
git add src/jsMain/kotlin/com/bkahlert/netmon/ui/pulse.kt src/jsMain/kotlin/com/bkahlert/netmon/UiSettings.kt src/jsMain/kotlin/com/bkahlert/netmon/ui/network.kt src/jsTest/kotlin/com/bkahlert/netmon/ui/PulseKtTest.kt
git commit -m "perf(display): pulse the radar icons for ten seconds after a scan"
```

### Task 17: the stable section ticks once a minute

**Files:**
- Modify: `src/jsMain/kotlin/com/bkahlert/netmon/stores.kt`, `src/jsMain/kotlin/com/bkahlert/netmon/ui/network.kt`
- Test: `src/jsTest/kotlin/com/bkahlert/netmon/CurrentTimeStoreTest.kt`,
  `src/jsTest/kotlin/com/bkahlert/netmon/ui/NetworkKtTest.kt`

**Interfaces:**
- Produces: `object MinuteClock : CurrentTimeStore(1.minutes)`; `RenderContext.hosts(hosts: Store<List<Host>>, clock:
  Flow<Instant> = CurrentTimeStore.data, classes: String? = null)`; `RenderContext.host(host: Store<Host>, clock:
  Flow<Instant>, highlightDuration: Duration = ...)`.

- [ ] **Step 1: Write the failing tests**

`CurrentTimeStoreTest.kt`:

```kotlin
package com.bkahlert.netmon

import com.bkahlert.netmon.fritz2.runTest
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class CurrentTimeStoreTest {

    @Test
    fun ticks_at_its_interval() = runTest {
        val store = CurrentTimeStore(refreshInterval = 50.milliseconds)
        val initial = store.current

        delay(200)

        store.current shouldBeGreaterThan initial
    }

    @Test
    fun the_minute_clock_ticks_once_a_minute() {
        MinuteClock.refreshInterval shouldBe 1.minutes
    }
}
```

`NetworkKtTest.kt`:

```kotlin
package com.bkahlert.netmon.ui

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.fritz2.runTest
import dev.fritz2.core.RootStore
import dev.fritz2.core.render
import io.kotest.matchers.string.shouldContain
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class NetworkKtTest {

    @Test
    fun a_cards_since_text_follows_the_clock_it_is_given() = runTest {
        val now = Clock.System.now()
        val clock = MutableStateFlow(now)
        val store = RootStore(listOf(Host(ip = IP.of("192.168.1.1"), status = Status.UP, since = now - 30.seconds)), job = job)
        val container = document.createElement("div") as HTMLElement
        document.body?.appendChild(container)
        render(container) { hosts(store, clock = clock) }
        delay(50)
        container.textContent shouldContain "since 30s"

        clock.value = now + 90.seconds
        delay(50)

        container.textContent shouldContain "since 2m"
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew jsBrowserTest --tests 'com.bkahlert.netmon.CurrentTimeStoreTest' --tests
'com.bkahlert.netmon.ui.NetworkKtTest'`
Expected: compilation FAIL on `MinuteClock`, `refreshInterval` visibility and the `clock` parameter

- [ ] **Step 3: Implement**

In `stores.kt`, `CurrentTimeStore`'s constructor parameter becomes `val refreshInterval: Duration =
UiSettings.REFRESH_INTERVAL`, and after the class, with `import kotlin.time.Duration.Companion.minutes`:

```kotlin
/** The clock of the stable section, whose texts change hourly at most. */
object MinuteClock : CurrentTimeStore(1.minutes)
```

In `network.kt`: `scan` calls `hosts(unstableHosts)` and `hosts(stableHosts, clock = MinuteClock.data, classes =
"opacity-50 [zoom:0.75]")`; `hosts` gains `clock: Flow<Instant> = CurrentTimeStore.data` before `classes` and passes it
to `host(hosts.mapByElement(value, Host::ip), clock)`; `host` gains `clock: Flow<Instant>` after `host` and computes
`val elapsedTime: Flow<Duration?> = clock.combine(host.data) { now, h -> h.getElapsedTime(now) }`. Imports:
`com.bkahlert.netmon.MinuteClock`, `kotlin.time.Instant`.

- [ ] **Step 4: Run the tests, then commit**

Run: `./gradlew jsBrowserTest`. Expected: PASS.

```bash
git add src/jsMain/kotlin/com/bkahlert/netmon/stores.kt src/jsMain/kotlin/com/bkahlert/netmon/ui/network.kt src/jsTest/kotlin/com/bkahlert/netmon/CurrentTimeStoreTest.kt src/jsTest/kotlin/com/bkahlert/netmon/ui/NetworkKtTest.kt
git commit -m "perf(display): tick the stable section once a minute"
```

### Task 18: dead weight out of the bundle

**Files:**
- Modify: `build.gradle.kts`, `webpack.config.d/tailwind.config.js`, `src/jsMain/resources/utils.css`,
  `src/jsMain/kotlin/com/bkahlert/netmon/ui/icon.kt`

- [ ] **Step 1: Record the bundle size**

Run: `./gradlew jsBrowserDistribution && ls -l build/dist/js/productionExecutable/netmon.js`. Note the size.

- [ ] **Step 2: Remove**

In `build.gradle.kts`, `jsMain`: delete `implementation("dev.fritz2:headless:$fritz2Version")`, the
`@tailwindcss/typography` line and the `tailwind-heropatterns` line with their comments. In `tailwind.config.js`,
`plugins: []` replaces the two `require(...)` entries. In `utils.css`, delete the three blocks marked `Delete, if not
needed` (the safe-area `:root`, `.prose-box`, `.debug`) so that the file keeps the three `@tailwind` directives and the
`@layer base` block. In `icon.kt`, `Aria.hidden to "true"` becomes `"aria-hidden" to "true"` and the headless import
goes.

- [ ] **Step 3: Verify**

Run: `./gradlew jsBrowserTest jsBrowserDistribution && ls -l build/dist/js/productionExecutable/netmon.js`
Expected: PASS and a smaller bundle; note both sizes for the pull request. `kotlin-js-store/yarn.lock` changes with the
removed packages; commit it.

```bash
git add build.gradle.kts webpack.config.d/tailwind.config.js src/jsMain/resources/utils.css src/jsMain/kotlin/com/bkahlert/netmon/ui/icon.kt kotlin-js-store/yarn.lock
git commit -m "perf(display): drop the unused tailwind plugins, prototyping css and headless"
```

Run: `make gradle && make test-tier2`. Expected: PASS, `display.png` and `kiosk.png` show the page.

### Task 19: the A/Bs in the VM, then the board

- [ ] **Step 1: The variants**

Write these files under `dist/variants/` (not committed); each is a complete `kiosk.conf`:

`00-baseline.conf`:

```
URL=http://localhost/?broker.host=localhost&broker.port=8080
COG_PLATFORM_DRM_VIDEO_MODE=800x480
```

`01-cog.conf`: the baseline plus `COG_ARGS="--doc-viewer --web-mem-limit=200 --web-check-interval=10
--web-kill-threshold=0.95 --webprocess-failure=restart"`.

`02-jit-tiers.conf`: `01-cog.conf` plus `JSC_useDFGJIT=false` and `JSC_useFTLJIT=false`.

`03-jit-off.conf`: `01-cog.conf` plus `JSC_useJIT=false` and `JSC_logGC=1`.

`04-paint.conf`: `02-jit-tiers.conf` plus `WEBKIT_SKIA_CPU_PAINTING_THREADS=1`.

- [ ] **Step 2: Run them**

For each variant: `make soak KIOSK_CONF=dist/variants/<file> SOAK_ARGS=--soak-duration=5m && cp dist/tier2/soak.md
dist/variants/<file>.md && cp dist/tier2/kiosk.png dist/variants/<file>.png` (the kiosk screenshot comes from the boot
test; run `make test-tier2` once for the chosen variant instead if a picture per variant is wanted). Each run boots a
fresh VM, about eight minutes, then soaks five.

Expected: five tables. For `03-jit-off.conf` the journal of `pihero-kiosk` shows GC lines, proving the environment
reaches the web process. Compare the kiosk's RAM+zram, the web process's private dirty bytes and the top CPU lines.

- [ ] **Step 3: Choose and record**

Put the winning combination into `devices/sample/user-data` (Task 14's lines, adjusted), and the five last rows into the
spec's Numbers section under `Kiosk A/Bs in the VM`. If the JIT-off variant wins on memory and the display test still
passes within its wait, it is the sample's choice; otherwise the two tiers off stay.

```bash
git add devices/sample/user-data docs/superpowers/specs/2026-10-02-footprint-design.md
git commit -m "perf(kiosk): set the kiosk's knobs from the a/b soaks"
```

Ask the user before pushing; then push and `gh pr create` with the title `perf(kiosk): a memory limit for the web
process and a calmer page`.

- [ ] **Step 4: The board**

After the pull request merged and `make deploy TARGET=pi@netmon.local` installed the display (the hook restarts the
units): write the chosen lines into the board's `/etc/pihero/kiosk.conf` with `sudo tee`, then `sudo systemctl restart
pihero-kiosk`. Run `uv run --frozen pytest -m boot --target=ssh --target-uri=pi@netmon.local tests/test_boot.py -k
Kiosk` and `make soak TARGET=pi@netmon.local`. Expected: PASS; the board's row goes into the spec's Numbers under `After
the kiosk changes`. The GPU painting A/B is board-only: repeat the soak once with `WEBKIT_SKIA_ENABLE_CPU_RENDERING=0`
added and compare a photo or `sudo cat /sys/kernel/debug/dri/0/state` and the numbers; keep it only if it wins.

## Phase 5: the finish line

### Task 20: the gate

- [ ] **Step 1: Compare**

In the spec's Numbers section, set the board's last soak row against the budget: the two units' RAM+zram against RAM
minus everything else minus apt's VM peak minus 40 MB. Write one sentence under the table: within the budget, or over by
how much and on which side.

- [ ] **Step 2: Decide**

Within: continue with Task 21. Over: stop here, write the finding into the spec's Numbers section, commit it on a
`docs/footprint-gate` branch, and tell the user which side is over and by how much; the escalation named in the spec is
a new brainstorm.

### Task 21: apt next to the live stack on the board

- [ ] **Step 1: Ask**

Tell the user: the probe moves `/etc/apt/apt.conf.d/52netmon-dpkg` to `/root/52netmon-dpkg.off` for the run and moves it
back if the probe fails. Wait for their yes.

- [ ] **Step 2: Run**

```bash
ssh pi@netmon.local 'sudo mv /etc/apt/apt.conf.d/52netmon-dpkg /root/52netmon-dpkg.off'
make apt-probe TARGET=pi@netmon.local
```

Expected: `apt probe: ok; <seconds> s, apt MemoryPeak=<bytes>, ...` and `dist/ssh/apt-probe.md`. On any other outcome:
`ssh pi@netmon.local 'sudo mv /root/52netmon-dpkg.off /etc/apt/apt.conf.d/52netmon-dpkg'`, record the outcome in the
spec's Numbers, and report.

- [ ] **Step 3: Record**

The summary line and the table's last row go into the spec's Numbers section under `The finish line`. Commit on
`docs/footprint-finish-line`, ask before pushing.

### Task 22: choam.de, the release, the upgrade

**Files in the choam.de repository:** `docs/hosts/pi/netmon/user-data.tmpl`, `ansible-inventory/host_vars/netmon.yml`,
`docs/hosts/pi/netmon/README.md`.

- [ ] **Step 1: The template**

Delete the `52netmon-dpkg` entry under `write_files` with its comment lines, which begin `# the stack is stopped before
apt starts`. Replace the kiosk entry's content with the sample's lines from Task 19. Remove `JAVA_TOOL_OPTIONS` from any
`scanner.conf` entry; if a scanner entry exists only for it, delete the entry.

- [ ] **Step 2: The playbook**

Delete `ansible-inventory/host_vars/netmon.yml`; the playbook's `apt_stop_units | default([])` handles its absence.

- [ ] **Step 3: The runbook**

In the Netmon table, the scanner row describes `/usr/lib/netmon/netmon-scanner`, a GraalVM native image as the system
user `netmon`, `NETMON_SCANNER_OPTIONS=-Xmx48m`, and the measured `MemoryMax`; `JAVA_TOOL_OPTIONS` leaves the overrides
list and `NETMON_SCANNER_OPTIONS` joins it. The memory paragraph becomes the before-and-after table from the spec's
Numbers section with the date of the last soak. The tripwire "stop the stack before `apt`, every time" becomes a note:
apt runs next to the stack since netmon 2.0.0, with the probe's wall time and apt's peak from `dist/ssh/apt-probe.md`,
and `make apt-probe TARGET=pi@netmon.local` as the way to re-check after a change. The `sudo systemctl stop pihero-kiosk
netmon-scanner` line leaves the command block. The kiosk section's `Cog and its two WPE processes take about 80 MB.`
gets the measured figure. The zram tripwire stays.

The user commits and pushes choam.de themselves, or asks.

- [ ] **Step 4: Release**

In netmon, on `main` after every pull request merged: `make release VERSION=2.0.0`, which runs tiers 0 to 2 and tags.
Ask before `git push origin v2.0.0`. The release workflow publishes `netmon-scanner_2.0.0_all.deb`, whose control says
`arm64`, and the display package.

- [ ] **Step 5: The board upgrades from the repository, next to the live stack**

With the hook gone from the board (Task 21 left it in `/root/52netmon-dpkg.off`; delete it after the user's yes): `ssh
pi@netmon.local 'sudo apt update && sudo apt upgrade -y'` without stopping anything, timed. Expected: both packages at
2.0.0, both units active throughout, `uptime` continuous. Then `uv run --frozen pytest -m 'installed or boot'
--target=ssh --target-uri=pi@netmon.local`. Expected: PASS. The outcome goes into the runbook's note.
