import dataclasses
import json

import pytest

from bench_figures import Figures, Settle
from bench_report import Header, Run, render, run_line, write_payloads

pytestmark = pytest.mark.tier0


class TestRender:
    def test_shows_a_single_run_as_its_values(self):
        result = render(header(["a"]), [Run(1, "a", phases(web_cpu=12.34))])

        assert "| scan 2 | web CPU s | 12.3 |" in result

    def test_shows_more_runs_as_median_and_range(self):
        runs = [Run(n, "a", phases(web_cpu=cpu)) for n, cpu in ((1, 10.0), (2, 14.0), (3, 11.0))]

        result = render(header(["a"], runs=3), runs)

        assert "| scan 2 | web CPU s | 11.0 (10.0–14.0) |" in result

    def test_gives_the_change_against_the_first_variant(self):
        runs = [Run(1, "a", phases(web_cpu=20.0, settle=Settle(40, True))), Run(2, "b", phases(web_cpu=15.0, settle=Settle(25, True)))]

        result = render(header(["a", "b"]), runs)

        assert "| scan 2 | web CPU s | 20.0 | 15.0 | -25 % |" in result
        assert "| scan 2 | settle s | 40 | 25 | -15 s |" in result

    def test_marks_a_change_within_overlapping_ranges(self):
        runs = [Run(1, "a", phases(web_cpu=10.0)), Run(2, "b", phases(web_cpu=11.0)), Run(3, "a", phases(web_cpu=12.0)), Run(4, "b", phases(web_cpu=13.0))]

        result = render(header(["a", "b"], runs=2), runs)

        assert "| scan 2 | web CPU s | 11.0 (10.0–12.0) | 12.0 (11.0–13.0) | ~+9 % |" in result

    def test_counts_only_valid_runs(self):
        runs = [Run(1, "a", phases(web_cpu=10.0)), Run(2, "a", None, "the board rebooted"), Run(3, "a", phases(web_cpu=12.0))]

        result = render(header(["a"], runs=3), runs)

        assert "|  | valid runs | 2/3 |" in result
        assert "| scan 2 | web CPU s | 11.0 (10.0–12.0) |" in result

    def test_shows_a_variant_without_a_valid_run_as_na(self):
        runs = [Run(1, "a", phases(web_cpu=10.0)), Run(2, "b", None, "no metrics for 15 s")]

        result = render(header(["a", "b"]), runs)

        assert "|  | valid runs | 1/1 | 0/1 |  |" in result
        assert "| scan 2 | web CPU s | 10.0 | n/a | n/a |" in result

    def test_gives_no_change_against_a_zero(self):
        runs = [Run(1, "a", phases(faults=0)), Run(2, "b", phases(faults=3))]

        result = render(header(["a", "b"]), runs)

        assert "| scan 2 | major faults | 0 | 3 | n/a |" in result

    def test_shows_an_unsettled_phase_as_at_least_its_length(self):
        result = render(header(["a"]), [Run(1, "a", phases(settle=Settle(90, False)))])

        assert "| scan 2 | settle s | > 90 |" in result

    def test_counts_the_unsettled_runs_in_a_median(self):
        runs = [Run(1, "a", phases(settle=Settle(40, True))), Run(2, "a", phases(settle=Settle(90, False)))]

        result = render(header(["a"], runs=2), runs)

        assert "| scan 2 | settle s | 65 (40–90), 1 unsettled |" in result

    def test_has_no_settle_time_for_the_load(self):
        result = render(header(["a"]), [Run(1, "a", phases())])

        assert "| load | settle s |" not in result

    def test_lists_every_run_in_order_with_the_reason_of_a_failed_one(self):
        runs = [Run(1, "a", phases()), Run(2, "b", None, "the kiosk restarted")]

        result = render(header(["a", "b"]), runs)

        rows = [line for line in result.splitlines() if line.startswith(("| 1 |", "| 2 |"))]
        assert rows[0].startswith("| 1 | a | load |")
        assert rows[-1] == "| 2 | b | failed: the kiosk restarted |"

    def test_names_the_target_the_order_and_the_stopped_scanner(self):
        result = render(header(["a", "b"]), [])

        assert "pi@netmon.local" in result
        assert "in this order: a, b" in result
        assert "scanner was stopped" in result


class TestRunLine:
    def test_names_each_phases_web_cpu_and_settle_time(self):
        result = run_line(Run(2, "main a33715a", phases(web_cpu=18.3, settle=Settle(25, True))), total=6)

        assert result == "run 2/6 main a33715a: load 18.3 s web CPU; scan 1 18.3 s web CPU, settle 25 s; scan 2 18.3 s web CPU, settle 25 s"

    def test_names_the_reason_of_a_failed_run(self):
        result = run_line(Run(3, "b", None, "the board rebooted"), total=6)

        assert result == "run 3/6 b: failed: the board rebooted"


class TestWritePayloads:
    def test_writes_the_meta_then_each_payload_on_a_line(self, tmp_path):
        path = tmp_path / "runs" / "1-a.jsonl"

        write_payloads(path, {"t0": 1000.0}, [b'{"resourceMetrics": []}', b"", b'{"a": 1}'])

        assert [json.loads(line) for line in path.read_text().splitlines()] == [{"t0": 1000.0}, {"resourceMetrics": []}, {"a": 1}]


def header(labels: list[str], runs: int = 1) -> Header:
    return Header(target="pi@netmon.local", date="2026-10-04 20:00", boot_id="b", labels=labels, runs=runs, order=labels * runs)


def phases(web_cpu: float = 10.0, faults: int = 5, settle: Settle = Settle(20, True)) -> dict[str, Figures]:
    figures = Figures(web_cpu=web_cpu, kiosk_cpu=12.0, kiosk_memory=150 * 2**20, kiosk_memory_peak=160 * 2**20, web_anon=60 * 2**20, faults=faults, settle=settle)
    return {"load": dataclasses.replace(figures, settle=None), "scan 1": figures, "scan 2": figures}
