"""The soak's and the apt probe's view of the board: the OTLP/JSON messages of netmon-metrics as samples, and a Markdown table."""
import json
import queue
import time
from collections.abc import Callable, Iterator
from contextlib import contextmanager
from dataclasses import dataclass

import paho.mqtt.client as mqtt

from booted import Tunnel

SCANNER = "netmon-scanner.service"
KIOSK = "pihero-kiosk.service"
UNITS = (SCANNER, KIOSK)
WEB_PROCESS = "WPEWebProcess"
SCANNER_PROCESS = "netmon-scanner"
SERVICE = "netmon-metrics"
TOPIC = "dt/netmon/+/metrics"
INTERVAL = 5
SILENCE = 3 * INTERVAL
JITTER = 1
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
    mem_total: int | None
    mem_available: int | None
    swap_free: int | None
    load1: float | None
    pswpin: int
    pswpout: int
    pgmajfault: int
    pressure_full_seconds: float | None
    zram_used: int | None
    web_anon: int | None
    web_swap: int | None
    scanner_rss: int | None
    scanner_anon: int | None
    web_pid: int | None
    web_start: int | None
    web_cpu_seconds: float | None


@dataclass(frozen=True)
class Sample:
    at: float
    boot_id: str
    units: dict[str, UnitSample]
    system: SystemSample


@dataclass(frozen=True)
class Point:
    attributes: dict[str, str]
    time: int
    start: int | None
    value: int | float


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


def decode(payload: bytes) -> Sample | None:
    """Return the sample in an OTLP/JSON message of netmon-metrics, or None for an empty payload, the topic cleared."""
    if not payload:
        return None
    entities = [(attributes(r.get("resource", {}).get("attributes")), points(r)) for r in json.loads(payload).get("resourceMetrics", [])]
    host_attributes, host = next(((a, p) for a, p in entities if a.get("service.name") == SERVICE), ({}, {}))
    units = {a["systemd.unit.name"]: unit_sample(p) for a, p in entities if "systemd.unit.name" in a and "process.pid" not in a}
    processes = {a["process.executable.name"]: (a, p) for a, p in entities if "process.pid" in a}
    web_attributes, web = processes.get(WEB_PROCESS, ({}, {}))
    _, scanner = processes.get(SCANNER_PROCESS, ({}, {}))
    cpu_time = web.get("process.cpu.time", [])
    system = SystemSample(
        mem_total=value(host, "system.memory.limit"),
        mem_available=value(host, "system.memory.linux.available"),
        swap_free=value(host, "system.paging.usage", {"system.paging.state": "free"}),
        load1=value(host, "system.linux.cpu.load_1m"),
        pswpin=int(value(host, "system.paging.operations", {"system.paging.direction": "in"}) or 0),
        pswpout=int(value(host, "system.paging.operations", {"system.paging.direction": "out"}) or 0),
        pgmajfault=int(value(host, "system.paging.faults", {"system.paging.fault.type": "major"}) or 0),
        pressure_full_seconds=value(host, "system.linux.memory.pressure.stall_time", {"kind": "full"}),
        zram_used=value(host, "system.linux.zram.memory.usage"),
        web_anon=value(web, "process.linux.memory.usage", {"type": "anon"}),
        web_swap=value(web, "process.linux.memory.usage", {"type": "swap"}),
        scanner_rss=value(scanner, "process.memory.usage"),
        scanner_anon=value(scanner, "process.linux.memory.usage", {"type": "anon"}),
        web_pid=int(web_attributes["process.pid"]) if web_attributes else None,
        web_start=cpu_time[0].start if cpu_time else None,
        web_cpu_seconds=sum(p.value for p in cpu_time) if cpu_time else None,
    )
    at = max((p.time for _, by_name in entities for found in by_name.values() for p in found), default=0) / 1e9
    empty = UnitSample(active="", restarts=0, current=None, swap_current=None, peak=None, swap_peak=None, anon=None, file=None, oom_kills=0)
    return Sample(at=at, boot_id=host_attributes.get("host.boot.id", ""), units={unit: units.get(unit, empty) for unit in UNITS}, system=system)


def attributes(items: list[dict] | None) -> dict[str, str]:
    return {item["key"]: next(iter(item.get("value", {}).values()), "") for item in items or []}


def points(resource_metrics: dict) -> dict[str, list[Point]]:
    result = {}
    for scope in resource_metrics.get("scopeMetrics", []):
        for metric in scope.get("metrics", []):
            data = metric.get("gauge") or metric.get("sum") or {}
            result[metric["name"]] = [
                Point(
                    attributes=attributes(p.get("attributes")),
                    time=int(p.get("timeUnixNano", 0)),
                    start=int(p["startTimeUnixNano"]) if "startTimeUnixNano" in p else None,
                    value=float(p["asDouble"]) if "asDouble" in p else int(p.get("asInt", 0)),
                )
                for p in data.get("dataPoints", [])
            ]
    return result


def value(by_name: dict[str, list[Point]], name: str, wanted: dict[str, str] | None = None) -> int | float | None:
    for point in by_name.get(name, []):
        if all(point.attributes.get(key) == expected for key, expected in (wanted or {}).items()):
            return point.value
    return None


def unit_sample(by_name: dict[str, list[Point]]) -> UnitSample:
    active = next((p.attributes.get("systemd.unit.active_state", "") for p in by_name.get("systemd.unit.state", []) if p.value == 1), "")
    return UnitSample(
        active=active,
        restarts=int(value(by_name, "systemd.unit.restarts") or 0),
        current=value(by_name, "systemd.unit.memory.usage", {"type": "ram"}),
        swap_current=value(by_name, "systemd.unit.memory.usage", {"type": "swap"}),
        peak=value(by_name, "systemd.unit.memory.peak", {"type": "ram"}),
        swap_peak=value(by_name, "systemd.unit.memory.peak", {"type": "swap"}),
        anon=value(by_name, "systemd.unit.memory.usage", {"type": "anon"}),
        file=value(by_name, "systemd.unit.memory.usage", {"type": "file"}),
        oom_kills=int(value(by_name, "systemd.unit.memory.oom_kills") or 0),
    )


class Samples:
    """The samples among the payloads put into a queue, as they arrive."""

    def __init__(self, payloads: queue.Queue, clock: Callable[[], float] = time.time):
        self.payloads = payloads
        self.clock = clock

    def next(self, timeout: float = SILENCE) -> Sample:
        """Return the next sample less than three intervals from the clock; raise ConnectionError when none arrives within `timeout` seconds."""
        deadline = time.monotonic() + timeout
        while (left := deadline - time.monotonic()) > 0:
            try:
                sample = decode(self.payloads.get(timeout=left))
            except queue.Empty:
                break
            if sample is not None and abs(self.clock() - sample.at) < SILENCE:
                return sample
        raise ConnectionError(f"no metrics for {timeout:.0f} s")


@contextmanager
def subscribed(target) -> Iterator[Samples]:
    """Yield the samples the target publishes, received over an ssh tunnel to its websocket listener."""
    tunnel = Tunnel(target)
    payloads = queue.Queue()
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, transport="websockets")
    client.on_connect = lambda c, userdata, flags, reason, properties: c.subscribe(TOPIC, qos=1)
    client.on_message = lambda c, userdata, message: payloads.put(message.payload)
    try:
        client.connect("127.0.0.1", tunnel.ws)
        client.loop_start()
        yield Samples(payloads)
    finally:
        client.loop_stop()
        client.disconnect()
        tunnel.close()


def thin(samples: list[Sample], every: float) -> list[Sample]:
    """Return the first sample and then the first one at least `every` seconds, less a second of jitter, after the one kept before."""
    kept = []
    for sample in samples:
        # A sample is stamped when the sampler wakes, a few milliseconds either side of the interval.
        if not kept or sample.at - kept[-1].at >= every - JITTER:
            kept.append(sample)
    return kept


def render_table(samples: list[Sample], limits: dict[str, str]) -> str:
    """Return the soak as Markdown: the limits, one row per sample with the swap, fault, cpu and stall counters as deltas."""
    first = samples[0]
    limit_text = ", ".join(f"{unit} memory.max={limit}" for unit, limit in limits.items())
    lines = [
        f"Boot id {first.boot_id}. MemTotal {mb(first.system.mem_total)} MB. Limits: {limit_text}.",
        "",
        "| t | scanner RAM+zram | scanner anon/file | scanner rss/anon | kiosk RAM+zram | kiosk anon/file | web anon/swap | Δweb cpu | available | swap free | zram pool | load | Δswpin | Δswpout | Δmajflt | PSI full % |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    previous = first
    for sample in samples:
        s, k, sys = sample.units[SCANNER], sample.units[KIOSK], sample.system
        lines.append(
            f"| {sample.at - first.at:.0f} "
            f"| {mb(s.current)}+{mb(s.swap_current)} | {mb(s.anon)}/{mb(s.file)} "
            f"| {mb(sys.scanner_rss)}/{mb(sys.scanner_anon)} "
            f"| {mb(k.current)}+{mb(k.swap_current)} | {mb(k.anon)}/{mb(k.file)} "
            f"| {mb(sys.web_anon)}/{mb(sys.web_swap)} | {web_cpu(previous.system, sys)} "
            f"| {mb(sys.mem_available)} | {mb(sys.swap_free)} | {mb(sys.zram_used)} | {load(sys.load1)} "
            f"| {delta(previous, sample, 'pswpin')} | {delta(previous, sample, 'pswpout')} | {delta(previous, sample, 'pgmajfault')} "
            f"| {stalled(previous, sample)} |"
        )
        previous = sample
    return "\n".join(lines) + "\n"


def render_summary(samples: list[Sample]) -> str:
    def peak(unit: str) -> int | None:
        totals = [(u.current or 0) + (u.swap_current or 0) for s in samples if (u := s.units[unit]).current is not None or u.swap_current is not None]
        return max(totals, default=None)

    def size(value: int | None) -> str:
        return "n/a" if value is None else f"{mb(value)} MB"

    web = max((s.system.web_anon for s in samples if s.system.web_anon is not None), default=None)
    scanner_rss = max((s.system.scanner_rss for s in samples if s.system.scanner_rss is not None), default=None)
    cpu = web_cpu(samples[0].system, samples[-1].system)
    cpu_text = "" if cpu == "n/a" else f", web process cpu {cpu} s"
    first, last = samples[0], samples[-1]
    faults = "n/a" if first.boot_id != last.boot_id else f"{(last.system.pgmajfault - first.system.pgmajfault) / max(last.at - first.at, 1):.1f}"
    return (
        f"soak: scanner peak {size(peak(SCANNER))}, scanner rss up to {size(scanner_rss)}, kiosk peak {size(peak(KIOSK))} RAM+zram, "
        f"web process anon up to {size(web)}{cpu_text}, {faults} major faults/s"
    )


def web_cpu(before: SystemSample, after: SystemSample) -> str:
    """Return the web process's CPU seconds between two samples, or n/a without one or across a replaced process."""
    if before.web_cpu_seconds is None or after.web_cpu_seconds is None or (before.web_pid, before.web_start) != (after.web_pid, after.web_start):
        return "n/a"
    return f"{after.web_cpu_seconds - before.web_cpu_seconds:.1f}"


def delta(before: Sample, after: Sample, counter: str) -> str:
    """Return the change of a host counter of `SystemSample` between two samples, or n/a across a reboot."""
    if before.boot_id != after.boot_id:
        return "n/a"
    return str(getattr(after.system, counter) - getattr(before.system, counter))


def stalled(before: Sample, after: Sample) -> str:
    """Return the percentage of the time between two samples that every task waited for memory, or n/a without the stall time or across a reboot."""
    first, last = before.system.pressure_full_seconds, after.system.pressure_full_seconds
    if first is None or last is None or after.at <= before.at or before.boot_id != after.boot_id:
        return "n/a"
    return f"{(last - first) / (after.at - before.at) * 100:.2f}"


def mb(value: int | None) -> str:
    return "n/a" if value is None else f"{value / MIB:.0f}"


def load(value: float | None) -> str:
    return "n/a" if value is None else f"{value:.1f}"
