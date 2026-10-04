"""Samples for the benchmark's tests."""
from sampling import KIOSK, SCANNER, Sample, SystemSample, UnitSample

MIB = 2**20


def sample(
    at: float,
    web_cpu: float | None = 0.0,
    kiosk_cpu: float | None = 0.0,
    web_pid: int | None = 1234,
    restarts: int = 0,
    oom_kills: int = 0,
    boot_id: str = "b",
    faults: int = 0,
    kiosk_ram: int | None = 100 * MIB,
    kiosk_swap: int | None = 10 * MIB,
    web_anon: int | None = 60 * MIB,
    web_started: float = 0.0,
) -> Sample:
    kiosk = UnitSample(active="active", restarts=restarts, current=kiosk_ram, swap_current=kiosk_swap, peak=None, swap_peak=None, anon=None, file=None, oom_kills=oom_kills, cpu_seconds=kiosk_cpu)
    scanner = UnitSample(active="inactive", restarts=0, current=None, swap_current=None, peak=None, swap_peak=None, anon=None, file=None, oom_kills=0, cpu_seconds=None)
    system = SystemSample(
        mem_total=None, mem_available=None, swap_free=None, load1=None, pswpin=0, pswpout=0, pgmajfault=faults, pressure_full_seconds=None, zram_used=None,
        web_anon=web_anon, web_swap=None, scanner_rss=None, scanner_anon=None, web_pid=web_pid, web_start=None if web_pid is None else int(web_started * 1e9), web_cpu_seconds=web_cpu,
    )
    return Sample(at=at, boot_id=boot_id, units={SCANNER: scanner, KIOSK: kiosk}, system=system)


def at_utilizations(utilizations: list[float], start: float = 1000.0, interval: float = 5.0, **kwargs) -> list[Sample]:
    cpu, result = 0.0, [sample(start, web_cpu=0.0, **kwargs)]
    for index, utilization in enumerate(utilizations, 1):
        cpu += utilization / 100 * interval
        result.append(sample(start + index * interval, web_cpu=cpu, **kwargs))
    return result
