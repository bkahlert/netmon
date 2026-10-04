"""The benchmark's report: a legend, a summary per phase with a column per variant, every run's figures, and the raw payloads."""
import json
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from statistics import median

from bench_figures import BUSY_ABOVE, PHASES, WINDOW, Figures
from sampling import INTERVAL

MIB = 2**20
LISTED = 5
FIGURES: list[tuple[str, Callable[[Figures], float | None], str, int]] = [
    ("load time", lambda f: f.load_time, "s", 0),
    ("CPU usage", lambda f: f.cpu, "%", 0),
    ("busy", lambda f: f.busy, "%", 0),
    ("memory peak", lambda f: f.memory_peak, "MB", 0),
    ("memory at end", lambda f: f.memory, "MB", 0),
    ("renderer memory", lambda f: f.web_anon, "MB", 0),
    ("kiosk CPU time", lambda f: f.kiosk_cpu, "s", 1),
    ("major faults", lambda f: f.faults, "", 0),
]
SUMMARY = {
    PHASES[0]: ("load time", "CPU usage", "memory peak"),
    PHASES[1]: ("CPU usage", "busy", "memory peak"),
    PHASES[2]: ("CPU usage", "busy", "memory peak"),
}


@dataclass(frozen=True)
class Run:
    number: int
    label: str
    figures: dict[str, Figures] | None
    problem: str | None = None


@dataclass(frozen=True)
class Header:
    target: str
    date: str
    boot_id: str
    labels: list[str]
    runs: int
    order: list[str]
    titles: dict[str, str]


def render(header: Header, runs: list[Run]) -> str:
    """Return the report as Markdown: what ran where, what the figures mean, the summary, and the runs in the order they ran."""
    return "\n".join([
        f"# Benchmark on {header.target}",
        "",
        f"{header.date}, boot id {header.boot_id}. {header.runs} run(s) per variant, in this order: {', '.join(header.order)}. "
        "The board's scanner was stopped.",
        "",
        LEGEND,
        "",
        summary(header, runs),
        "",
        "## Details",
        "",
        details(runs),
        "",
    ])


LEGEND = "\n".join([
    "Each run restarts the kiosk on the variant's page, then plays the scans the headings name; a scan lasts until the next one or the run's end.",
    "",
    "- **load time**: from systemd starting the kiosk until the kiosk reports the page loaded.",
    "- **CPU usage**: the page's renderer process (WPEWebProcess) in % of one core, as `top` shows it; above 100 % it uses more than one core. "
    "The page load's counts from the renderer's start.",
    f"- **busy**: the share of a scan until the renderer's CPU usage over the next {WINDOW * INTERVAL} s is under {BUSY_ABOVE:.0f} %; "
    "100 % means it was still busy when the scan ended.",
    "- **memory peak**: the kiosk's highest RAM plus compressed swap (zram) during the phase.",
    f"- A cell lists each valid run's value in the order they ran; with more than {LISTED} runs, the median and the range. "
    "Δ compares the medians with the first variant; **about the same** when the runs' ranges overlap.",
    "- The status bar's **pills** stay empty: the fake broker carries no metrics, and pages before #54 fetch `stats.json`, which the "
    "benchmark's server does not have.",
])


def summary(header: Header, runs: list[Run]) -> str:
    labels, rest = header.labels, header.labels[1:]
    columns = len(labels) + len(rest)
    lines = [
        "|  | " + " | ".join([*labels, *(f"Δ {label}" for label in rest)]) + " |",
        "|" + "---|" * (1 + columns),
        "| valid runs | " + " | ".join(valid(label, runs) for label in labels) + " |" + "  |" * len(rest),
    ]
    for phase in PHASES:
        lines.append(f"| **{header.titles[phase]}** |" + "  |" * columns)
        for name, get, unit, decimals in FIGURES:
            if name not in SUMMARY[phase]:
                continue
            values = {label: [v for run in runs if run.label == label and run.figures and (v := get(run.figures[phase])) is not None] for label in labels}
            cells = [cell(values[label], unit, decimals) for label in labels] + [delta(values[labels[0]], values[label]) for label in rest]
            lines.append(f"| {name} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def details(runs: list[Run]) -> str:
    lines = [
        "| " + " | ".join(["run", "variant", "phase", *(f"{name} {unit}".strip() for name, _, unit, _ in FIGURES)]) + " |",
        "|" + "---|" * (3 + len(FIGURES)),
    ]
    for run in runs:
        if run.figures is None:
            lines.append(f"| {run.number} | {run.label} | failed: {run.problem} |")
            continue
        for phase in PHASES:
            cells = [number(v, unit, decimals) if (v := get(run.figures[phase])) is not None else "" for _, get, unit, decimals in FIGURES]
            lines.append(f"| {run.number} | {run.label} | {phase} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def run_line(run: Run, total: int) -> str:
    """Return the terminal's line for a finished run."""
    if run.figures is None:
        return f"run {run.number}/{total} {run.label}: failed: {run.problem}"
    load = run.figures[PHASES[0]]
    parts = [f"{PHASES[0]} {short(load.load_time, 's')}, {short(load.cpu, '%')} CPU"]
    for phase in PHASES[1:]:
        figures = run.figures[phase]
        parts.append(f"{phase} {short(figures.cpu, '%')} CPU, busy {short(figures.busy, '%')}")
    return f"run {run.number}/{total} {run.label}: " + "; ".join(parts)


def write_payloads(path: Path, meta: dict, payloads: list[bytes]) -> None:
    """Write `meta` and then every non-empty payload as one JSON line each, creating the directory."""
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [json.dumps(meta)] + [json.dumps(json.loads(payload)) for payload in payloads if payload]
    path.write_text("\n".join(lines) + "\n")


def valid(label: str, runs: list[Run]) -> str:
    own = [run for run in runs if run.label == label]
    return f"{sum(run.figures is not None for run in own)} of {len(own)}"


def cell(values: list[float], unit: str, decimals: int) -> str:
    if not values:
        return "n/a"
    if len(values) <= LISTED:
        return " · ".join(with_unit(number(v, unit, decimals), unit) for v in values)
    return f"{with_unit(number(median(values), unit, decimals), unit)} ({number(min(values), unit, decimals)}–{with_unit(number(max(values), unit, decimals), unit)})"


def delta(reference: list[float], values: list[float]) -> str:
    if not reference or not values:
        return "n/a"
    before, after = median(reference), median(values)
    if before == 0:
        return "n/a"
    if len(reference) > 1 and len(values) > 1 and max(min(reference), min(values)) <= min(max(reference), max(values)):
        return "about the same"
    return f"{(after - before) / before * 100:+.0f} %"


def short(value: float | None, unit: str) -> str:
    return "n/a" if value is None else with_unit(number(value, unit, 0), unit)


def number(value: float, unit: str, decimals: int) -> str:
    return f"{value / MIB if unit == 'MB' else value:.{decimals}f}"


def with_unit(text: str, unit: str) -> str:
    return f"{text} {unit}" if unit else text
