"""Readers for the soak and the apt probe: systemd's memory properties, the unit cgroups, /proc counters, and a Markdown table."""
import time
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
