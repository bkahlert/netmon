"""The benchmark's report: a summary per phase and figure with a column per variant, every run's figures, and the raw payloads."""
import json
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from statistics import median

from bench_figures import PHASES, Figures, Settle

MIB = 2**20
FIGURES: list[tuple[str, Callable[[Figures], float | Settle | None], str]] = [
    ("web CPU s", lambda f: f.web_cpu, "cpu"),
    ("kiosk CPU s", lambda f: f.kiosk_cpu, "cpu"),
    ("kiosk RAM+zram MB", lambda f: f.kiosk_memory, "memory"),
    ("kiosk RAM+zram peak MB", lambda f: f.kiosk_memory_peak, "memory"),
    ("web anon MB", lambda f: f.web_anon, "memory"),
    ("major faults", lambda f: f.faults, "count"),
    ("settle s", lambda f: f.settle, "settle"),
]


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


def render(header: Header, runs: list[Run]) -> str:
    """Return the report as Markdown: what ran where, the summary, and the runs in the order they ran."""
    return "\n".join([
        f"# Benchmark on {header.target}",
        "",
        f"{header.date}, boot id {header.boot_id}. {header.runs} run(s) per variant, in this order: {', '.join(header.order)}. "
        "The board's scanner was stopped.",
        "",
        summary(header.labels, runs),
        "",
        "## Runs",
        "",
        per_run(runs),
        "",
    ])


def summary(labels: list[str], runs: list[Run]) -> str:
    rest = labels[1:]
    lines = [
        "| " + " | ".join(["phase", "figure", *labels, *(f"Δ {label}" for label in rest)]) + " |",
        "|" + "---|" * (2 + len(labels) + len(rest)),
        "|  | valid runs | " + " | ".join(valid(label, runs) for label in labels) + " |" + "  |" * len(rest),
    ]
    for phase in PHASES:
        for name, get, kind in FIGURES:
            if phase == PHASES[0] and kind == "settle":
                continue
            values = {label: [v for run in runs if run.label == label and run.figures and (v := get(run.figures[phase])) is not None] for label in labels}
            cells = [cell(values[label], kind) for label in labels] + [delta(values[labels[0]], values[label], kind) for label in rest]
            lines.append(f"| {phase} | {name} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def per_run(runs: list[Run]) -> str:
    lines = [
        "| " + " | ".join(["run", "variant", "phase", *(name for name, _, _ in FIGURES)]) + " |",
        "|" + "---|" * (3 + len(FIGURES)),
    ]
    for run in runs:
        if run.figures is None:
            lines.append(f"| {run.number} | {run.label} | failed: {run.problem} |")
            continue
        for phase in PHASES:
            cells = [cell([v], kind) if (v := get(run.figures[phase])) is not None else "" for _, get, kind in FIGURES]
            lines.append(f"| {run.number} | {run.label} | {phase} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def run_line(run: Run, total: int) -> str:
    """Return the terminal's line for a finished run."""
    if run.figures is None:
        return f"run {run.number}/{total} {run.label}: failed: {run.problem}"
    parts = []
    for phase in PHASES:
        figures = run.figures[phase]
        text = f"{phase} {fmt(figures.web_cpu, 'cpu')} s web CPU"
        if figures.settle is not None:
            text += f", settle {cell([figures.settle], 'settle')} s"
        parts.append(text)
    return f"run {run.number}/{total} {run.label}: " + "; ".join(parts)


def write_payloads(path: Path, meta: dict, payloads: list[bytes]) -> None:
    """Write `meta` and then every non-empty payload as one JSON line each, creating the directory."""
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [json.dumps(meta)] + [json.dumps(json.loads(payload)) for payload in payloads if payload]
    path.write_text("\n".join(lines) + "\n")


def valid(label: str, runs: list[Run]) -> str:
    own = [run for run in runs if run.label == label]
    return f"{sum(run.figures is not None for run in own)}/{len(own)}"


def cell(values: list, kind: str) -> str:
    if not values:
        return "n/a"
    numbers = [number(v) for v in values]
    text = fmt(median(numbers), kind) if len(numbers) == 1 else f"{fmt(median(numbers), kind)} ({fmt(min(numbers), kind)}–{fmt(max(numbers), kind)})"
    if kind == "settle":
        unsettled = sum(not v.settled for v in values)
        if unsettled and len(values) == 1:
            return f"> {text}"
        if unsettled:
            text += f", {unsettled} unsettled"
    return text


def delta(reference: list, values: list, kind: str) -> str:
    if not reference or not values:
        return "n/a"
    ours, theirs = [number(v) for v in reference], [number(v) for v in values]
    before, after = median(ours), median(theirs)
    if kind == "settle":
        text = f"{after - before:+.0f} s"
    elif before == 0:
        return "n/a"
    else:
        text = f"{(after - before) / before * 100:+.0f} %"
    overlapping = len(ours) > 1 and len(theirs) > 1 and max(min(ours), min(theirs)) <= min(max(ours), max(theirs))
    return f"~{text}" if overlapping else text


def number(value: float | Settle) -> float:
    return value.seconds if isinstance(value, Settle) else value


def fmt(value: float | None, kind: str) -> str:
    if value is None:
        return "n/a"
    if kind == "cpu":
        return f"{value:.1f}"
    if kind == "memory":
        return f"{value / MIB:.0f}"
    return f"{value:.0f}"
