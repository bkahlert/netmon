import pytest

from bench_figures import Timeline, busy, figures, problem, utilization
from bench_fixtures import MIB, at_utilizations, sample

pytestmark = pytest.mark.tier0


class TestUtilization:
    def test_is_the_cpu_seconds_over_the_elapsed_seconds_in_percent(self):
        result = utilization(sample(0, web_cpu=2.0), sample(10, web_cpu=3.0))

        assert result == pytest.approx(10.0)

    def test_is_none_without_a_web_process(self):
        result = utilization(sample(0, web_cpu=None, web_pid=None), sample(5, web_cpu=1.0))

        assert result is None


class TestBusy:
    def test_is_the_share_of_the_phase_until_three_calm_intervals_begin(self):
        result = busy(at_utilizations([120, 110, 40, 30, 20, 20], start=0))

        assert result == pytest.approx(100 / 3)

    def test_is_zero_on_a_calm_start(self):
        result = busy(at_utilizations([20, 20, 20], start=0))

        assert result == 0

    def test_averages_out_a_single_busy_interval(self):
        result = busy(at_utilizations([20, 80, 20, 20], start=0))

        assert result == 0

    def test_needs_more_than_two_calm_intervals(self):
        result = busy(at_utilizations([120, 20, 20, 120, 120], start=0))

        assert result == 100

    def test_reads_a_noisy_idle_as_calm(self):
        result = busy(at_utilizations([119, 122, 125, 127, 136, 142, 27, 35, 15, 36, 24, 48], start=0))

        assert result == pytest.approx(50)

    def test_is_full_for_a_renderer_busy_all_along(self):
        result = busy(at_utilizations([120] * 12, start=0))

        assert result == 100


class TestFigures:
    def test_takes_the_page_load_from_the_counters_at_t0(self):
        timeline = Timeline(before=sample(0, faults=100), load=[], samples=run(web_started=990), boundaries=[0, 12], load_time=21.0)

        result = figures(timeline)["page load"]

        assert (result.load_time, result.cpu, result.kiosk_cpu, result.faults) == (21.0, pytest.approx(80.0), pytest.approx(9.0), 20)

    def test_peaks_the_page_load_over_the_new_web_process_only(self):
        old = sample(3, web_pid=1, kiosk_ram=500 * MIB)
        new = sample(6, web_pid=1234, kiosk_ram=200 * MIB)
        timeline = Timeline(before=sample(0, web_pid=1), load=[old, new], samples=run(), boundaries=[0, 12])

        result = figures(timeline)["page load"]

        assert result.memory_peak == 210 * MIB

    def test_takes_each_scan_phase_from_its_boundaries(self):
        timeline = Timeline(before=sample(0), load=[], samples=run(), boundaries=[0, 12])

        result = figures(timeline)

        assert (result["scan 1"].cpu, result["scan 2"].cpu) == (pytest.approx(10.0), pytest.approx(10.0))
        assert (result["scan 1"].kiosk_cpu, result["scan 2"].kiosk_cpu) == (pytest.approx(12 * 0.6), pytest.approx(18 * 0.6))
        assert result["scan 1"].faults == 12
        assert result["scan 2"].memory == 110 * MIB

    def test_peaks_a_scan_phase_without_its_first_sample(self):
        timeline = Timeline(before=sample(0), load=[], samples=run(ram_at={12: 900 * MIB}), boundaries=[0, 12])

        result = figures(timeline)

        assert result["scan 1"].memory_peak == 910 * MIB
        assert result["scan 2"].memory_peak == 110 * MIB

    def test_gives_the_scans_alone_a_busy_share(self):
        timeline = Timeline(before=sample(0), load=[], samples=run(), boundaries=[0, 12])

        result = figures(timeline)

        assert (result["page load"].busy, result["scan 1"].busy, result["scan 2"].busy) == (None, 0, 0)

    def test_has_no_load_time_without_one(self):
        timeline = Timeline(before=sample(0), load=[], samples=run(), boundaries=[0, 12])

        result = figures(timeline)["page load"]

        assert result.load_time is None


class TestProblem:
    def test_is_none_for_a_clean_run(self):
        result = problem(Timeline(before=sample(0, web_pid=1), load=[], samples=run(), boundaries=[0, 12]))

        assert result is None

    def test_names_a_web_process_that_is_the_one_before_the_restart(self):
        result = problem(Timeline(before=sample(0, web_pid=1234), load=[], samples=run(), boundaries=[0, 12]))

        assert result == "the kiosk did not restart: the web process at t0 is the one before"

    @pytest.mark.parametrize("changed, message", [
        ({"boot_id": "other"}, "the board rebooted"),
        ({"web_pid": 99}, "the web process was replaced: 1234 → 99"),
        ({"restarts": 1}, "the kiosk restarted"),
        ({"oom_kills": 1}, "the kiosk was OOM-killed"),
    ])
    def test_names_what_went_wrong_after_t0(self, changed, message):
        samples = run()
        samples[20] = sample(samples[20].at, **changed)

        result = problem(Timeline(before=sample(0, web_pid=1), load=[], samples=samples, boundaries=[0, 12]))

        assert result == message


def run(ram_at=None, web_started=0.0):
    ram_at = ram_at or {}
    return [
        sample(1000 + 5 * index, web_cpu=8.0 + 0.5 * index, kiosk_cpu=9.0 + 0.6 * index, faults=120 + index, kiosk_ram=ram_at.get(index, 100 * MIB), web_started=web_started)
        for index in range(31)
    ]
