"""The benchmark's figures: one run's samples cut into phases, their CPU usage, busy share, memory and faults."""
from dataclasses import dataclass

from netmon_dev.system.sampling import KIOSK, Sample

PHASES = ("page load", "scan 1", "scan 2")
BUSY_ABOVE = 50.0
WINDOW = 3


@dataclass(frozen=True)
class Timeline:
    """One run's samples: the last before the kiosk's restart, those of the restart, and from t0 to the end with the index each scan followed."""

    before: Sample
    load: list[Sample]
    samples: list[Sample]
    boundaries: list[int]
    load_time: float | None = None


@dataclass(frozen=True)
class Figures:
    load_time: float | None
    cpu: float | None
    busy: float | None
    memory_peak: int | None
    memory: int | None
    web_anon: int | None
    kiosk_cpu: float | None
    faults: int


def utilization(before: Sample, after: Sample) -> float | None:
    """Return the web process's CPU between two samples in % of one core, or None where either has no figure."""
    if before.system.web_cpu_seconds is None or after.system.web_cpu_seconds is None or after.at <= before.at:
        return None
    return (after.system.web_cpu_seconds - before.system.web_cpu_seconds) / (after.at - before.at) * 100


def busy(phase: list[Sample]) -> float:
    """Return the share of the phase in % until the web process's CPU over the next three intervals is under 50 % of one core, 100 when it never is."""
    for index in range(len(phase) - WINDOW):
        u = utilization(phase[index], phase[index + WINDOW])
        if u is not None and u < BUSY_ABOVE:
            return (phase[index].at - phase[0].at) / (phase[-1].at - phase[0].at) * 100
    return 100.0


def figures(timeline: Timeline) -> dict[str, Figures]:
    """Return the figures of each phase: the page load from the counters at t0, each scan phase from its boundary samples."""
    samples, t0 = timeline.samples, timeline.samples[0]
    own = [s for s in [*timeline.load, t0] if s.system.web_pid == t0.system.web_pid]
    result = {
        PHASES[0]: Figures(
            load_time=timeline.load_time,
            cpu=since_start(t0),
            busy=None,
            memory_peak=peak(own),
            memory=memory(t0),
            web_anon=t0.system.web_anon,
            kiosk_cpu=t0.units[KIOSK].cpu_seconds,
            faults=t0.system.pgmajfault - timeline.before.system.pgmajfault,
        )
    }
    ends = [*timeline.boundaries[1:], len(samples) - 1]
    for name, start, end in zip(PHASES[1:], timeline.boundaries, ends):
        phase = samples[start:end + 1]
        first, last = phase[0], phase[-1]
        result[name] = Figures(
            load_time=None,
            cpu=utilization(first, last),
            busy=busy(phase),
            memory_peak=peak(phase[1:]),
            memory=memory(last),
            web_anon=last.system.web_anon,
            kiosk_cpu=difference(first.units[KIOSK].cpu_seconds, last.units[KIOSK].cpu_seconds),
            faults=last.system.pgmajfault - first.system.pgmajfault,
        )
    return result


def problem(timeline: Timeline) -> str | None:
    """Return why the run does not count, or None."""
    first = timeline.samples[0]
    if first.system.web_pid is None:
        return "no web process at t0"
    if first.system.web_pid == timeline.before.system.web_pid:
        return "the kiosk did not restart: the web process at t0 is the one before"
    for s in timeline.samples[1:]:
        if s.boot_id != first.boot_id:
            return "the board rebooted"
        if s.system.web_pid != first.system.web_pid:
            return f"the web process was replaced: {first.system.web_pid} → {s.system.web_pid}"
        if s.units[KIOSK].restarts != first.units[KIOSK].restarts:
            return "the kiosk restarted"
        if s.units[KIOSK].oom_kills != first.units[KIOSK].oom_kills:
            return "the kiosk was OOM-killed"
    return None


def since_start(sample: Sample) -> float | None:
    started, cpu = sample.system.web_start, sample.system.web_cpu_seconds
    if started is None or cpu is None or sample.at <= started / 1e9:
        return None
    return cpu / (sample.at - started / 1e9) * 100


def memory(sample: Sample) -> int | None:
    kiosk = sample.units[KIOSK]
    return None if kiosk.current is None else kiosk.current + (kiosk.swap_current or 0)


def peak(samples: list[Sample]) -> int | None:
    return max((m for s in samples if (m := memory(s)) is not None), default=None)


def difference(before: float | None, after: float | None) -> float | None:
    return None if before is None or after is None else after - before
