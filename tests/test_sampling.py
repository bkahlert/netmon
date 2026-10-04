import queue
from pathlib import Path

import pytest

from sampling import KIOSK, SCANNER, Sample, Samples, SystemSample, UnitSample, decode, parse_duration, parse_show, render_summary, render_table, thin

pytestmark = pytest.mark.tier0
GOLDEN = Path(__file__).resolve().parents[1] / "metrics" / "testdata" / "metrics.json"


class TestParseDuration:
    def test_minutes_seconds_hours_and_bare_seconds(self):
        assert [parse_duration(t) for t in ("10m", "30s", "1h", "90")] == [600, 30, 3600, 90]

    def test_rejects_an_unknown_unit(self):
        with pytest.raises(ValueError):
            parse_duration("5d")


class TestParseShow:
    def test_splits_on_the_first_equals_sign(self):
        result = parse_show("ActiveState=active\nNRestarts=0\nMemoryCurrent=46403584\n")

        assert result == {"ActiveState": "active", "NRestarts": "0", "MemoryCurrent": "46403584"}


class TestDecode:
    def test_reads_the_time_and_the_boot_id(self):
        result = decode(GOLDEN.read_bytes())

        assert result.at == 1759450005
        assert result.boot_id == "6f1c2a8e-2c4b-4a51-9d3e-0b7c1e2f3a4d"

    def test_reads_the_units(self):
        result = decode(GOLDEN.read_bytes())

        assert result.units[SCANNER] == UnitSample(active="active", restarts=0, current=41943040, swap_current=31457280, peak=62914560, swap_peak=41943040, anon=31457280, file=10485760, oom_kills=0)
        assert result.units[KIOSK].restarts == 1
        assert result.units[KIOSK].current == 160000000

    def test_reads_the_system_and_the_processes(self):
        result = decode(GOLDEN.read_bytes())

        assert result.system == SystemSample(
            mem_total=435159040, mem_available=97599488, swap_free=167772160, load1=3.1, pswpin=117856217, pswpout=98765, pgmajfault=126517772,
            pressure_full_seconds=4.25, zram_used=69357568, web_anon=63504384, web_swap=115343360, scanner_rss=47185920, scanner_anon=32505856,
            web_pid=1234, web_start=1759400045 * 10**9, web_cpu_seconds=pytest.approx(7.7),
        )

    def test_an_absent_unit_is_empty_and_not_active(self):
        result = decode(b'{"resourceMetrics":[]}')

        assert result.units[KIOSK] == UnitSample(active="", restarts=0, current=None, swap_current=None, peak=None, swap_peak=None, anon=None, file=None, oom_kills=0)

    def test_an_empty_payload_is_no_sample(self):
        assert decode(b"") is None


class TestSamples:
    def test_returns_the_next_sample(self):
        payloads = queue.Queue()
        payloads.put(GOLDEN.read_bytes())

        result = Samples(payloads, clock=lambda: 1759450006).next(timeout=0.1)

        assert result.at == 1759450005

    def test_skips_an_empty_payload(self):
        payloads = queue.Queue()
        payloads.put(b"")
        payloads.put(GOLDEN.read_bytes())

        result = Samples(payloads, clock=lambda: 1759450006).next(timeout=0.1)

        assert result.at == 1759450005

    def test_drops_a_sample_three_intervals_from_the_clock(self):
        payloads = queue.Queue()
        payloads.put(GOLDEN.read_bytes())

        with pytest.raises(ConnectionError):
            Samples(payloads, clock=lambda: 1759450020).next(timeout=0.1)

    def test_raises_a_connection_error_on_silence(self):
        with pytest.raises(ConnectionError, match="no metrics"):
            Samples(queue.Queue()).next(timeout=0.1)


class TestThin:
    def test_keeps_the_first_and_then_one_sample_per_interval(self):
        samples = [sample(at) for at in (0, 5, 10, 15, 20, 25, 30, 35)]

        result = thin(samples, 15)

        assert [s.at for s in result] == [0, 15, 30]

    def test_keeps_a_sample_a_little_early_for_its_interval(self):
        samples = [sample(at) for at in (0, 5.01, 10.02, 14.998, 20.01, 25.0, 29.995)]

        result = thin(samples, 15)

        assert [s.at for s in result] == [0, 14.998, 29.995]

    def test_keeps_one_sample_per_interval_off_the_sampling_grid(self):
        samples = [sample(at) for at in (0, 5, 10, 15, 20, 25, 30, 35)]

        result = thin(samples, 12)

        assert [s.at for s in result] == [0, 15, 30]


class TestRenderTable:
    def test_has_a_row_per_sample_with_deltas_and_na_for_missing_values(self):
        samples = [sample(0, pswpin=100, pressure=None), sample(30, pswpin=160, pressure=0.3)]

        result = render_table(samples, {SCANNER: "335544320", KIOSK: "314572800"})

        assert "| 0 |" in result and "| 30 |" in result
        assert "| 60 |" in result
        assert "n/a" in result
        assert "335544320" in result

    def test_shows_the_share_of_the_interval_stalled_in_percent(self):
        samples = [sample(0, pressure=1.0), sample(30, pressure=1.3)]

        result = render_table(samples, {})

        assert "| PSI full % |" in result
        assert result.rstrip().splitlines()[5].endswith("| 1.00 |")

    def test_shows_memtotal_after_the_boot_id_and_the_zram_pool_after_swap_free(self):
        samples = [sample(0, zram_used=69 * 2**20)]

        result = render_table(samples, {})

        assert "Boot id b. MemTotal 415 MB." in result
        assert "| swap free | zram pool | load |" in result
        assert "| 160 | 69 | 3.1 |" in result

    def test_shows_the_scanner_process_after_its_cgroup_anon_and_file(self):
        samples = [sample(0, scanner_rss=45 * 2**20, scanner_anon=31 * 2**20)]

        result = render_table(samples, {})

        assert "| scanner anon/file | scanner rss/anon | kiosk RAM+zram |" in result
        assert "| 30/10 | 45/31 | 50+100 |" in result

    def test_shows_the_web_process_cpu_seconds_since_the_previous_sample(self):
        samples = [sample(0, web_cpu=10.0), sample(30, web_cpu=12.5)]

        result = render_table(samples, {})

        assert "| web anon/swap | Δweb cpu | available |" in result
        assert "| 60/110 | 0.0 | 90 |" in result
        assert "| 60/110 | 2.5 | 90 |" in result

    def test_shows_na_for_the_web_process_cpu_across_a_replaced_web_process(self):
        samples = [sample(0, web_cpu=10.0), sample(30, web_cpu=0.5, web_start=2)]

        result = render_table(samples, {})

        assert "| 60/110 | n/a | 90 |" in result
        assert "-9.5" not in result

    def test_shows_na_for_the_host_deltas_across_a_reboot(self):
        samples = [sample(0, pswpin=100, pgmajfault=1000, pressure=4.0), sample(30, pswpin=10, pgmajfault=20, pressure=0.1, boot_id="c")]

        result = render_table(samples, {})

        last = result.rstrip().splitlines()[-1]
        assert last.endswith("| n/a | n/a | n/a | n/a |")
        assert "-" not in last

    def test_shows_na_for_an_unknown_load(self):
        samples = [sample(0, load1=None)]

        result = render_table(samples, {})

        assert "| 160 | 69 | n/a |" in result

    def test_has_no_top_excerpt(self):
        result = render_table([sample(0)], {})

        assert "Top CPU" not in result


class TestRenderSummary:
    def test_names_the_web_process_anon_and_cpu(self):
        samples = [sample(0, web_cpu=10.0), sample(30, web_cpu=12.5), sample(60, web_cpu=14.2)]

        result = render_summary(samples)

        assert "web process anon up to 60 MB, web process cpu 4.2 s, " in result

    def test_omits_the_web_process_cpu_across_a_replaced_web_process(self):
        samples = [sample(0, web_cpu=10.0), sample(30, web_cpu=12.5), sample(60, web_cpu=0.5, web_start=2)]

        result = render_summary(samples)

        assert "web process cpu" not in result

    def test_shows_na_for_the_major_faults_across_a_reboot(self):
        samples = [sample(0, pgmajfault=1000), sample(30, pgmajfault=20, boot_id="c")]

        result = render_summary(samples)

        assert result.endswith(", n/a major faults/s")

    def test_names_the_peaks(self):
        samples = [sample(0, kiosk_current=80 * 2**20), sample(30, kiosk_current=120 * 2**20)]

        result = render_summary(samples)

        assert "kiosk" in result and "220 MB" in result

    def test_names_the_scanner_rss_peak_after_the_scanner_peak(self):
        samples = [sample(0, scanner_rss=40 * 2**20), sample(30, scanner_rss=46 * 2**20)]

        result = render_summary(samples)

        assert "scanner peak 70 MB, scanner rss up to 46 MB, kiosk peak" in result

    def test_reports_an_unmeasured_peak_as_na(self):
        samples = [sample(at, kiosk_current=None, kiosk_swap_current=None, web_anon=None, scanner_rss=None) for at in (0, 30)]

        result = render_summary(samples)

        assert "scanner peak 70 MB" in result
        assert "kiosk peak n/a" in result
        assert "anon up to n/a" in result
        assert "scanner rss up to n/a" in result
        assert "web process cpu" not in result


def sample(
    at: float,
    pswpin: int = 0,
    pgmajfault: int = 0,
    pressure: float | None = None,
    kiosk_current: int | None = 50 * 2**20,
    kiosk_swap_current: int | None = 100 * 2**20,
    web_anon: int | None = 60 * 2**20,
    zram_used: int | None = 69 * 2**20,
    mem_total: int | None = 415 * 2**20,
    scanner_rss: int | None = 45 * 2**20,
    scanner_anon: int | None = 30 * 2**20,
    web_cpu: float | None = None,
    web_start: int = 1,
    load1: float | None = 3.1,
    boot_id: str = "b",
) -> Sample:
    scanner = UnitSample(active="active", restarts=0, current=40 * 2**20, swap_current=30 * 2**20, peak=60 * 2**20, swap_peak=40 * 2**20, anon=30 * 2**20, file=10 * 2**20, oom_kills=0)
    kiosk = UnitSample(active="active", restarts=0, current=kiosk_current, swap_current=kiosk_swap_current, peak=150 * 2**20, swap_peak=120 * 2**20, anon=50 * 2**20, file=20 * 2**20, oom_kills=0)
    system = SystemSample(
        mem_total=mem_total, mem_available=90 * 2**20, swap_free=160 * 2**20, load1=load1, pswpin=pswpin, pswpout=0, pgmajfault=pgmajfault,
        pressure_full_seconds=pressure, zram_used=zram_used, web_anon=web_anon, web_swap=110 * 2**20, scanner_rss=scanner_rss, scanner_anon=scanner_anon,
        web_pid=1234 if web_cpu is not None else None, web_start=web_start if web_cpu is not None else None, web_cpu_seconds=web_cpu,
    )
    return Sample(at=at, boot_id=boot_id, units={SCANNER: scanner, KIOSK: kiosk}, system=system)
