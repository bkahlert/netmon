"""make bench: the display's scripted benchmark on a board, each variant built on the workstation and served through the board's tunnel."""
import copy
import os
import shutil
import subprocess
import sys
import time
from collections.abc import Callable, Mapping
from contextlib import ExitStack
from dataclasses import dataclass
from pathlib import Path

from pihero_testkit.preview import board, process
from pihero_testkit.ssh import SshTarget

import bench_figures
import bench_report
import preview_broker
import sampling
import scan_fixtures
from bench_figures import Timeline
from bench_report import Header, Run
from layout import Page
from sampling import Sample

SCAN_TOPIC = "dt/netmon/node/wlan0/10.0.0.1/24/scan"
SCAN_TWO_AT = 60
END_AT = 150
GOES_DOWN = (0, 20, 30)
COMES_UP = (10, 24, 38)
GONE = 40
ROOT = Path(__file__).resolve().parents[1]
WORKING_TREE = "working-tree"
BENCH = ROOT / "dist" / "bench"
BUNDLES = BENCH / "bundles"
SOURCES = BENCH / "src"
DIST = Path("build") / "dist" / "js" / "productionExecutable"
GRADLE = ["./gradlew", "--no-daemon", "--console=plain", "jsBrowserDistribution"]
PAGE_PORT = 8082
SESSION = "netmon-bench"
METRICS_UNIT = "netmon-metrics.service"
SCANNER_UNIT = "netmon-scanner.service"
USAGE = 'usage: make bench TARGET=user@host[:port] [VARIANTS="ref ... ."] [RUNS=1]'


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
        received = stream.receive()
        payloads.append(received[1])
        return received[0]

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
        for known in variants:
            if known.ref == variant.ref:
                raise ValueError(f"VARIANTS names {ref} twice")
            if known.directory == variant.directory:
                raise ValueError(f"VARIANTS names one commit twice: {known.ref} and {ref} are both {variant.sha[:7]}")
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


def bundle(variant: Variant, run=subprocess.run) -> Path:
    """Build the variant's production bundle into dist/bench/bundles, a commit's once; raise CalledProcessError when git or Gradle fails."""
    target = BUNDLES / variant.directory
    if variant.ref == ".":
        run(GRADLE, cwd=ROOT, check=True)
        shutil.rmtree(target, ignore_errors=True)
        shutil.copytree(ROOT / DIST, target)
        return target
    if (target / "index.html").exists():
        return target
    source = SOURCES / variant.sha
    run(["git", "worktree", "add", "--detach", str(source), variant.sha], cwd=ROOT, check=True)
    try:
        run(GRADLE, cwd=source, check=True)
        partial = target.with_name(target.name + ".partial")
        shutil.rmtree(partial, ignore_errors=True)
        shutil.copytree(source / DIST, partial)
        shutil.rmtree(target, ignore_errors=True)
        partial.rename(target)
    finally:
        run(["git", "worktree", "remove", "--force", str(source)], cwd=ROOT, check=False)
    return target


def page_url(variant: Variant) -> str:
    """Return the variant's page as the board's kiosk reaches it, with the broker as the page reaches it."""
    return (
        f"http://127.0.0.1:{board.remote_port(PAGE_PORT)}/{variant.directory}/"
        f"?broker.host=127.0.0.1&broker.port={board.remote_port(preview_broker.WEBSOCKET_PORT)}"
    )


def run_all(planned: list[Variant], one_run: Callable[[int, Variant], Run], write: Callable[[list[Run]], None], report: Callable[[str], None]) -> list[Run]:
    """Run every planned run in order, writing the report and reporting a line after each."""
    runs = []
    for number, variant in enumerate(planned, 1):
        runs.append(one_run(number, variant))
        write(runs)
        report(bench_report.run_line(runs[-1], len(planned)))
    return runs


def bench(target: str, variants: list[Variant], runs: int, out: Path) -> None:
    """Run the benchmark on `target` and write its report into `out`; everything started on the way is ended on the way out."""
    planned = order(variants, runs)
    with ExitStack() as cleanup:
        session = board.Session(target, SESSION, out / board.LOG_NAME)
        session.check_kiosk()
        if session.ssh(f"systemctl is-active {METRICS_UNIT}").stdout.strip() != "active":
            raise RuntimeError(f"{target} has no active {METRICS_UNIT}; install the netmon-metrics package")
        if process.answers("127.0.0.1", PAGE_PORT):
            raise RuntimeError(f"port {PAGE_PORT} is taken on the workstation; end what serves there first")
        broker = preview_broker.Broker(preview_broker.FAKE, "localhost", preview_broker.WEBSOCKET_PORT)
        preview_broker.start(broker)
        cleanup.callback(preview_broker.stop)
        page = Page(BUNDLES, port=PAGE_PORT)
        cleanup.callback(page.close)
        inspector = process.free_port()
        ports = [PAGE_PORT, broker.port]
        tunnel = session.open_tunnel(board.forwards(ports, inspector), inspector, [board.remote_port(port) for port in ports])
        cleanup.callback(session.close_tunnel, tunnel)
        stream = cleanup.enter_context(sampling.subscribed(SshTarget(target, [])))
        print(f"stopping {SCANNER_UNIT} on {target} for the benchmark; a killed benchmark leaves it stopped until the next reboot", file=sys.stderr, flush=True)
        cleanup.callback(start_scanner, session)
        session.ssh(f"sudo systemctl stop {SCANNER_UNIT}")
        cleanup.callback(session.restore)
        header = Header(target=target, date=time.strftime("%Y-%m-%d %H:%M"), boot_id=stream.next().boot_id, labels=[v.label for v in variants], runs=runs, order=[v.label for v in planned])

        def one_run(number: int, variant: Variant) -> Run:
            payloads: list[bytes] = []
            conf = session.session_conf(page_url(variant))
            timeline, problem = None, None
            try:
                timeline = run_timeline(
                    stream,
                    clear=lambda: preview_broker.clear(SCAN_TOPIC),
                    install=lambda: session.install(conf, tunnel),
                    publish=lambda scan: preview_broker.publish({SCAN_TOPIC: scan}),
                    payloads=payloads,
                )
                problem = bench_figures.problem(timeline)
            except (TimeoutError, ConnectionError) as error:
                problem = str(error)
            meta = {"variant": variant.label, "problem": problem}
            if timeline is not None:
                meta |= {"t0": timeline.samples[0].at, "boundaries": [timeline.samples[i].at for i in timeline.boundaries]}
            bench_report.write_payloads(out / "runs" / f"{number}-{variant.directory}.jsonl", meta, payloads)
            return Run(number, variant.label, None if problem else bench_figures.figures(timeline), problem)

        report = out / "report.md"
        run_all(planned, one_run, write=lambda done: report.write_text(bench_report.render(header, done)), report=lambda line: print(line, file=sys.stderr, flush=True))
        print(f"report in {report}", file=sys.stderr, flush=True)


def start_scanner(session: board.Session) -> None:
    try:
        result = session.ssh(f"sudo systemctl start {SCANNER_UNIT}")
    except TimeoutError as error:
        print(f"could not start {SCANNER_UNIT}: {error}; a reboot of the board starts it", file=sys.stderr)
        return
    if result.returncode != 0:
        print(f"could not start {SCANNER_UNIT}: {result.stderr.strip()}; a reboot of the board starts it", file=sys.stderr)


def main(environ: Mapping[str, str] = os.environ) -> int:
    """Run make bench; return 0 when done, 2 with the message on a bad variable or a failure, 130 on Ctrl-C."""
    target = environ.get("TARGET")
    if not target:
        print(USAGE, file=sys.stderr)
        return 2
    try:
        runs = parse_runs(environ.get("RUNS"))
        variants = parse_variants(environ.get("VARIANTS"), git)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2
    process.raise_on_sigterm()
    try:
        for variant in variants:
            print(f"building {variant.label}", file=sys.stderr, flush=True)
            bundle(variant)
        bench(target, variants, runs, BENCH / time.strftime("%Y-%m-%d-%H%M"))
    except subprocess.CalledProcessError as error:
        print(f"{' '.join(error.cmd)} failed with status {error.returncode}", file=sys.stderr)
        return 2
    except (RuntimeError, TimeoutError, ConnectionError) as error:
        print(error, file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 130
    return 0


if __name__ == "__main__":
    sys.exit(main())
