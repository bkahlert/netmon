import pytest

from bench_figures import Settle, Timeline, baseline, figures, problem, settle, utilization
from bench_fixtures import MIB, at_utilizations, sample

pytestmark = pytest.mark.tier0


class TestUtilization:
    def test_is_the_cpu_seconds_over_the_elapsed_seconds_in_percent(self):
        result = utilization(sample(0, web_cpu=2.0), sample(10, web_cpu=3.0))

        assert result == pytest.approx(10.0)

    def test_is_none_without_a_web_process(self):
        result = utilization(sample(0, web_cpu=None, web_pid=None), sample(5, web_cpu=1.0))

        assert result is None


class TestBaseline:
    def test_averages_the_intervals_from_since(self):
        samples = at_utilizations([50, 50, 4, 6], start=0)

        result = baseline(samples, since=10)

        assert result == pytest.approx(5.0)

    def test_is_none_without_an_interval_from_since(self):
        result = baseline(at_utilizations([5, 5], start=0), since=20)

        assert result is None


class TestSettle:
    def test_is_where_the_first_two_calm_intervals_begin(self):
        phase = at_utilizations([40, 30, 6, 6.5, 6], start=0)

        result = settle(phase, base=5.0)

        assert result == Settle(10, True)

    def test_is_zero_on_a_calm_start(self):
        result = settle(at_utilizations([5, 5, 5], start=0), base=5.0)

        assert result == Settle(0, True)

    def test_needs_the_next_interval_calm_too(self):
        phase = at_utilizations([40, 6, 30, 6, 6], start=0)

        result = settle(phase, base=5.0)

        assert result == Settle(15, True)

    def test_is_the_phase_unsettled_on_a_last_calm_interval_alone(self):
        result = settle(at_utilizations([40, 40, 6], start=0), base=5.0)

        assert result == Settle(15, False)


class TestFigures:
    def test_takes_the_load_from_the_counters_at_t0(self):
        timeline = Timeline(before=sample(0, faults=100), load=[], samples=run(), boundaries=[0, 12])

        result = figures(timeline)["load"]

        assert (result.web_cpu, result.kiosk_cpu, result.faults) == (pytest.approx(8.0), pytest.approx(9.0), 20)

    def test_peaks_the_load_over_the_new_web_process_only(self):
        old = sample(3, web_pid=1, kiosk_ram=500 * MIB)
        new = sample(6, web_pid=1234, kiosk_ram=200 * MIB)
        timeline = Timeline(before=sample(0, web_pid=1), load=[old, new], samples=run(), boundaries=[0, 12])

        result = figures(timeline)["load"]

        assert result.kiosk_memory_peak == 210 * MIB

    def test_takes_each_scan_phase_from_its_boundaries(self):
        timeline = Timeline(before=sample(0), load=[], samples=run(), boundaries=[0, 12])

        result = figures(timeline)

        assert result["scan 1"].web_cpu == pytest.approx(12 * 0.5)
        assert result["scan 2"].web_cpu == pytest.approx(18 * 0.5)
        assert result["scan 1"].faults == 12
        assert result["scan 2"].kiosk_memory == 110 * MIB

    def test_peaks_a_scan_phase_without_its_first_sample(self):
        timeline = Timeline(before=sample(0), load=[], samples=run(ram_at={12: 900 * MIB}), boundaries=[0, 12])

        result = figures(timeline)

        assert result["scan 1"].kiosk_memory_peak == 910 * MIB
        assert result["scan 2"].kiosk_memory_peak == 110 * MIB

    def test_settles_the_scan_phases_against_the_last_20_s(self):
        timeline = Timeline(before=sample(0), load=[], samples=run(), boundaries=[0, 12])

        result = figures(timeline)

        assert (result["load"].settle, result["scan 1"].settle, result["scan 2"].settle) == (None, Settle(0, True), Settle(0, True))


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


def run(ram_at=None):
    ram_at = ram_at or {}
    return [
        sample(1000 + 5 * index, web_cpu=8.0 + 0.5 * index, kiosk_cpu=9.0 + 0.6 * index, faults=120 + index, kiosk_ram=ram_at.get(index, 100 * MIB))
        for index in range(31)
    ]
