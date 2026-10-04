"""The benchmark's figures: one run's samples cut into phases, their CPU, memory, faults and settle time."""
from dataclasses import dataclass

from sampling import KIOSK, Sample

PHASES = ("load", "scan 1", "scan 2")
MARGIN = 2.0
BASELINE_FROM = 130


@dataclass(frozen=True)
class Timeline:
    """One run's samples: the last before the kiosk's restart, those of the restart, and from t0 to the end with the index each scan followed."""

    before: Sample
    load: list[Sample]
    samples: list[Sample]
    boundaries: list[int]


@dataclass(frozen=True)
class Settle:
    """Seconds from a phase's start until the web process is calm, or the phase's length when it never is."""

    seconds: float
    settled: bool


@dataclass(frozen=True)
class Figures:
    web_cpu: float | None
    kiosk_cpu: float | None
    kiosk_memory: int | None
    kiosk_memory_peak: int | None
    web_anon: int | None
    faults: int
    settle: Settle | None


def utilization(before: Sample, after: Sample) -> float | None:
    """Return the web process's CPU between two samples in % of one core, or None where either has no figure."""
    if before.system.web_cpu_seconds is None or after.system.web_cpu_seconds is None or after.at <= before.at:
        return None
    return (after.system.web_cpu_seconds - before.system.web_cpu_seconds) / (after.at - before.at) * 100


def baseline(samples: list[Sample], since: float) -> float | None:
    """Return the mean utilization of the intervals that start at or after `since`, or None without one."""
    values = [u for a, b in zip(samples, samples[1:]) if a.at >= since and (u := utilization(a, b)) is not None]
    return sum(values) / len(values) if values else None


def settle(phase: list[Sample], base: float, margin: float = MARGIN) -> Settle:
    """Return where the first two consecutive intervals within `margin` points of `base` begin, or the phase unsettled."""
    calm = [(u := utilization(a, b)) is not None and u <= base + margin for a, b in zip(phase, phase[1:])]
    for index in range(len(calm) - 1):
        if calm[index] and calm[index + 1]:
            return Settle(phase[index].at - phase[0].at, True)
    return Settle(phase[-1].at - phase[0].at, False)


def figures(timeline: Timeline) -> dict[str, Figures]:
    """Return the figures of each phase: the load from the counters at t0, each scan phase from its boundary samples."""
    samples, t0 = timeline.samples, timeline.samples[0]
    base = baseline(samples, t0.at + BASELINE_FROM)
    own = [s for s in [*timeline.load, t0] if s.system.web_pid == t0.system.web_pid]
    result = {
        PHASES[0]: Figures(
            web_cpu=t0.system.web_cpu_seconds,
            kiosk_cpu=t0.units[KIOSK].cpu_seconds,
            kiosk_memory=memory(t0),
            kiosk_memory_peak=peak(own),
            web_anon=t0.system.web_anon,
            faults=t0.system.pgmajfault - timeline.before.system.pgmajfault,
            settle=None,
        )
    }
    ends = [*timeline.boundaries[1:], len(samples) - 1]
    for name, start, end in zip(PHASES[1:], timeline.boundaries, ends):
        phase = samples[start:end + 1]
        first, last = phase[0], phase[-1]
        result[name] = Figures(
            web_cpu=difference(first.system.web_cpu_seconds, last.system.web_cpu_seconds),
            kiosk_cpu=difference(first.units[KIOSK].cpu_seconds, last.units[KIOSK].cpu_seconds),
            kiosk_memory=memory(last),
            kiosk_memory_peak=peak(phase[1:]),
            web_anon=last.system.web_anon,
            faults=last.system.pgmajfault - first.system.pgmajfault,
            settle=None if base is None else settle(phase, base),
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


def memory(sample: Sample) -> int | None:
    kiosk = sample.units[KIOSK]
    return None if kiosk.current is None else kiosk.current + (kiosk.swap_current or 0)


def peak(samples: list[Sample]) -> int | None:
    return max((m for s in samples if (m := memory(s)) is not None), default=None)


def difference(before: float | None, after: float | None) -> float | None:
    return None if before is None or after is None else after - before
