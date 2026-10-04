import dataclasses
import json

import pytest

from bench_figures import Figures
from bench_report import Header, Run, render, run_line, write_payloads

pytestmark = pytest.mark.tier0


class TestRender:
    def test_lists_the_values_of_the_runs_in_their_order(self):
        runs = [Run(n, "a", phases(cpu=cpu)) for n, cpu in ((1, 116.4), (2, 115.2), (3, 127.0))]

        result = render(header(["a"], runs=3), runs)

        assert "| CPU usage | 116 % · 115 % · 127 % |" in section(result, SCAN_2)

    def test_shows_more_than_five_runs_as_median_and_range(self):
        runs = [Run(n, "a", phases(cpu=float(cpu))) for n, cpu in enumerate(range(10, 17), 1)]

        result = render(header(["a"], runs=7), runs)

        assert "| CPU usage | 13 % (10–16 %) |" in section(result, SCAN_2)

    def test_heads_a_further_variant_with_its_change(self):
        result = render(header(["a", "b"]), [])

        assert "|  | a | b | Δ b |" in result.splitlines()

    def test_gives_the_change_of_the_medians_against_the_first_variant(self):
        runs = [Run(1, "a", phases(cpu=20.0)), Run(2, "b", phases(cpu=15.0))]

        result = render(header(["a", "b"]), runs)

        assert "| CPU usage | 20 % | 15 % | -25 % |" in section(result, SCAN_2)

    def test_calls_a_change_within_overlapping_ranges_about_the_same(self):
        runs = [Run(1, "a", phases(cpu=10.0)), Run(2, "b", phases(cpu=11.0)), Run(3, "a", phases(cpu=12.0)), Run(4, "b", phases(cpu=13.0))]

        result = render(header(["a", "b"], runs=2), runs)

        assert "| CPU usage | 10 % · 12 % | 11 % · 13 % | about the same |" in section(result, SCAN_2)

    def test_counts_only_valid_runs(self):
        runs = [Run(1, "a", phases(cpu=10.0)), Run(2, "a", None, "the board rebooted"), Run(3, "a", phases(cpu=12.0))]

        result = render(header(["a"], runs=3), runs)

        assert "| valid runs | 2 of 3 |" in result
        assert "| CPU usage | 10 % · 12 % |" in section(result, SCAN_2)

    def test_shows_a_variant_without_a_valid_run_as_na(self):
        runs = [Run(1, "a", phases(cpu=10.0)), Run(2, "b", None, "no metrics for 15 s")]

        result = render(header(["a", "b"]), runs)

        assert "| valid runs | 1 of 1 | 0 of 1 |  |" in result
        assert "| CPU usage | 10 % | n/a | n/a |" in section(result, SCAN_2)

    def test_gives_no_change_against_a_zero(self):
        runs = [Run(1, "a", phases(busy=0.0)), Run(2, "b", phases(busy=50.0))]

        result = render(header(["a", "b"]), runs)

        assert "| busy | 0 % | 50 % | n/a |" in section(result, SCAN_2)

    def test_shows_a_renderer_busy_all_along_as_full(self):
        result = render(header(["a"]), [Run(1, "a", phases(busy=100.0))])

        assert "| busy | 100 % |" in section(result, SCAN_2)

    def test_shows_the_load_time_of_the_page_load_and_no_busy_share(self):
        result = render(header(["a"]), [Run(1, "a", phases(load_time=21.4))])

        assert "| load time | 21 s |" in section(result, PAGE_LOAD)
        assert not [line for line in section(result, PAGE_LOAD) if line.startswith("| busy |")]

    def test_shows_no_load_time_for_a_scan(self):
        result = render(header(["a"]), [Run(1, "a", phases())])

        assert not [line for line in section(result, SCAN_1) if line.startswith("| load time |")]

    def test_explains_its_figures_and_the_empty_pills(self):
        result = render(header(["a"]), [])

        assert [word for word in ("load time", "CPU usage", "busy", "memory peak", "pills") if f"**{word}**" not in result] == []

    def test_lists_every_run_in_order_with_the_reason_of_a_failed_one(self):
        runs = [Run(1, "a", phases()), Run(2, "b", None, "the kiosk restarted")]

        result = render(header(["a", "b"]), runs)

        rows = [line for line in result.splitlines() if line.startswith(("| 1 |", "| 2 |"))]
        assert rows[0].startswith("| 1 | a | page load | 21 | 50 |  | 160 | 150 | 60 | 12.0 | 5 |")
        assert rows[-1] == "| 2 | b | failed: the kiosk restarted |"

    def test_names_the_target_the_order_and_the_stopped_scanner(self):
        result = render(header(["a", "b"]), [])

        assert "pi@netmon.local" in result
        assert "in this order: a, b" in result
        assert "scanner was stopped" in result


class TestRunLine:
    def test_names_each_phases_cpu_usage_and_busy_share(self):
        result = run_line(Run(2, "28a4cf3", phases(cpu=79.4, busy=50.0)), total=6)

        assert result == "run 2/6 28a4cf3: page load 21 s, 79 % CPU; scan 1 79 % CPU, busy 50 %; scan 2 79 % CPU, busy 50 %"

    def test_names_the_reason_of_a_failed_run(self):
        result = run_line(Run(3, "b", None, "the board rebooted"), total=6)

        assert result == "run 3/6 b: failed: the board rebooted"


class TestWritePayloads:
    def test_writes_the_meta_then_each_payload_on_a_line(self, tmp_path):
        path = tmp_path / "runs" / "1-a.jsonl"

        write_payloads(path, {"t0": 1000.0}, [b'{"resourceMetrics": []}', b"", b'{"a": 1}'])

        assert [json.loads(line) for line in path.read_text().splitlines()] == [{"t0": 1000.0}, {"resourceMetrics": []}, {"a": 1}]


PAGE_LOAD = "page load"
SCAN_1 = "scan 1 (60 s): 53 hosts appear"
SCAN_2 = "scan 2 (90 s): 8 hosts change"


def header(labels: list[str], runs: int = 1) -> Header:
    titles = {"page load": PAGE_LOAD, "scan 1": SCAN_1, "scan 2": SCAN_2}
    return Header(target="pi@netmon.local", date="2026-10-04 20:00", boot_id="b", labels=labels, runs=runs, order=labels * runs, titles=titles)


def phases(cpu: float = 50.0, busy: float = 50.0, load_time: float = 21.0) -> dict[str, Figures]:
    scan = Figures(load_time=None, cpu=cpu, busy=busy, memory_peak=160 * 2**20, memory=150 * 2**20, web_anon=60 * 2**20, kiosk_cpu=12.0, faults=5)
    return {"page load": dataclasses.replace(scan, load_time=load_time, busy=None), "scan 1": scan, "scan 2": scan}


def section(report: str, title: str) -> list[str]:
    lines = report.splitlines()
    start = next(index for index, line in enumerate(lines) if line.startswith(f"| **{title}** |"))
    rest = lines[start + 1:]
    end = next((index for index, line in enumerate(rest) if line.startswith("| **") or not line.startswith("|")), len(rest))
    return rest[:end]
