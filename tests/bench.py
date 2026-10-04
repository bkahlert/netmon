"""make bench: the display's scripted benchmark on a board, each variant built on the workstation and served through the board's tunnel."""
import copy
import subprocess
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

import scan_fixtures
from bench_figures import Timeline
from sampling import Sample

SCAN_TOPIC = "dt/netmon/node/wlan0/10.0.0.1/24/scan"
SCAN_TWO_AT = 60
END_AT = 150
GOES_DOWN = (0, 20, 30)
COMES_UP = (10, 24, 38)
GONE = 40
ROOT = Path(__file__).resolve().parents[1]
WORKING_TREE = "working-tree"


@dataclass(frozen=True)
class Variant:
    ref: str
    sha: str
    dirty: bool = False

    @property
    def label(self) -> str:
        return f"{self.ref} {self.sha[:7]}{'+dirty' if self.dirty else ''}"

    @property
    def directory(self) -> str:
        return WORKING_TREE if self.ref == "." else self.sha


def scenario(t0: int) -> list[tuple[int, dict]]:
    """Return the scans of a run as (offset, scan) steps, every timestamp t0 plus an offset: the 14+39 fixture, then eight changes."""
    first = scan_fixtures.scans(1, 14, 39, now=t0)[SCAN_TOPIC]
    return [(0, first), (SCAN_TWO_AT, changed(first, t0 + SCAN_TWO_AT))]


def changed(scan: dict, at: int) -> dict:
    """Return `scan` as it is at `at`: three hosts down, three up, one new and one gone, each change since `at`."""
    hosts = copy.deepcopy(scan["hosts"])
    for index, status in [(i, "down") for i in GOES_DOWN] + [(i, "up") for i in COMES_UP]:
        hosts[index] |= {"status": status, "since": at}
    hosts = [host for index, host in enumerate(hosts) if index != GONE]
    hosts.append(scan_fixtures.host(0, len(scan["hosts"]), at))
    return {**scan, "hosts": hosts, "timestamp": at}


def run_timeline(
    stream,
    clear: Callable[[], None],
    install: Callable[[], None],
    publish: Callable[[dict], None],
    payloads: list[bytes],
    steps: Callable[[int], list[tuple[int, dict]]] = scenario,
    end_at: float = END_AT,
) -> Timeline:
    """Run the scenario once and return its samples, every payload of the run appended to `payloads`; raise ConnectionError on silence.

    The scan is cleared, then `install` restarts the kiosk on the page and returns once it loaded. The first sample after
    that is t0. Each scan goes out right after the first sample at or after its offset from t0, and the run ends at the
    first sample at or after `end_at`."""

    def receive() -> Sample:
        sample, payload = stream.receive()
        payloads.append(payload)
        return sample

    stream.pending()
    clear()
    before = receive()
    install()
    load = []
    for sample, payload in stream.pending():
        load.append(sample)
        payloads.append(payload)
    samples = [receive()]
    t0 = samples[0].at
    boundaries = []
    for offset, scan in steps(int(t0)):
        while samples[-1].at < t0 + offset:
            samples.append(receive())
        publish(scan)
        boundaries.append(len(samples) - 1)
    while samples[-1].at < t0 + end_at:
        samples.append(receive())
    return Timeline(before, load, samples, boundaries)


def parse_variants(text: str | None, git: Callable[..., str]) -> list[Variant]:
    """Return the variants VARIANTS names, `.` the working tree and the default; raise ValueError on a ref that is no commit or a commit named twice."""
    variants = []
    for ref in (text or "").split() or ["."]:
        if ref == ".":
            variant = Variant(".", git("rev-parse", "HEAD"), dirty=bool(git("status", "--porcelain")))
        else:
            try:
                variant = Variant(ref, git("rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}"))
            except subprocess.CalledProcessError:
                raise ValueError(f"VARIANTS names {ref!r}, which is no commit here") from None
        if any(known.directory == variant.directory for known in variants):
            raise ValueError(f"VARIANTS names {variant.label} twice")
        variants.append(variant)
    return variants


def parse_runs(text: str | None) -> int:
    """Return the runs per variant RUNS names, 1 by default; raise ValueError on anything but a positive whole number."""
    if not text:
        return 1
    if not text.isdigit() or int(text) < 1:
        raise ValueError(f"RUNS must be a whole number of at least 1, not {text!r}")
    return int(text)


def order(variants: list[Variant], runs: int) -> list[Variant]:
    """Return the runs in the order they go: the variants one after the other, `runs` times."""
    return [variant for _ in range(runs) for variant in variants]


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
