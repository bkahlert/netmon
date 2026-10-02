import pytest

from sampling import (
    KIOSK,
    SCANNER,
    Sample,
    SystemSample,
    UnitSample,
    bytes_or_none,
    parse_duration,
    parse_kb_lines,
    parse_key_values,
    parse_pressure,
    parse_show,
    parse_zram,
    render_summary,
    render_table,
)

pytestmark = pytest.mark.tier0


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


class TestBytesOrNone:
    def test_not_set_and_missing_are_none(self):
        assert bytes_or_none("[not set]") is None
        assert bytes_or_none(None) is None
        assert bytes_or_none("") is None

    def test_a_number_is_an_int(self):
        assert bytes_or_none("46403584") == 46403584


class TestParseKeyValues:
    def test_memory_stat_and_vmstat_lines(self):
        assert parse_key_values("anon 29335552\nfile 14327808\n") == {"anon": 29335552, "file": 14327808}
        assert parse_key_values("pswpin 117856217\npgmajfault 126517772\n") == {"pswpin": 117856217, "pgmajfault": 126517772}


class TestParseKbLines:
    def test_meminfo_and_smaps_rollup_lines_become_bytes(self):
        result = parse_kb_lines("MemAvailable:      95312 kB\nSwapFree:         170000 kB\nPrivate_Dirty:     62012 kB\n")

        assert result == {"MemAvailable": 95312 * 1024, "SwapFree": 170000 * 1024, "Private_Dirty": 62012 * 1024}


class TestParsePressure:
    def test_full_avg10(self):
        text = "some avg10=1.50 avg60=0.80 avg300=0.40 total=1234\nfull avg10=0.75 avg60=0.30 avg300=0.10 total=567\n"

        assert parse_pressure(text) == 0.75

    def test_missing_file_is_none(self):
        assert parse_pressure(None) is None
        assert parse_pressure("") is None


class TestParseZram:
    def test_mem_used_total_is_the_third_field(self):
        assert parse_zram("240640000 62609626 69357568        0 111222784       75 11928529     4924    55706\n") == 69357568

    def test_missing_is_none(self):
        assert parse_zram(None) is None


class TestRenderTable:
    def test_has_a_row_per_sample_with_deltas_and_na_for_missing_values(self):
        samples = [sample(0, pswpin=100, pressure=None), sample(30, pswpin=160, pressure=0.5)]

        result = render_table(samples, {SCANNER: "335544320", KIOSK: "314572800"})

        assert "| 0 |" in result and "| 30 |" in result
        assert "| 60 |" in result
        assert "n/a" in result
        assert "335544320" in result

    def test_summary_names_the_peaks(self):
        samples = [sample(0, kiosk_current=80 * 2**20), sample(30, kiosk_current=120 * 2**20)]

        result = render_summary(samples)

        assert "kiosk" in result and "220 MB" in result


def sample(at: float, pswpin: int = 0, pressure: float | None = None, kiosk_current: int = 50 * 2**20) -> Sample:
    scanner = UnitSample(active="active", restarts=0, current=40 * 2**20, swap_current=30 * 2**20, peak=60 * 2**20, swap_peak=40 * 2**20, anon=30 * 2**20, file=10 * 2**20, oom_kills=0)
    kiosk = UnitSample(active="active", restarts=0, current=kiosk_current, swap_current=100 * 2**20, peak=150 * 2**20, swap_peak=120 * 2**20, anon=50 * 2**20, file=20 * 2**20, oom_kills=0)
    system = SystemSample(mem_available=90 * 2**20, swap_free=160 * 2**20, load1=3.1, pswpin=pswpin, pswpout=0, pgmajfault=0, pressure_full10=pressure, zram_used=69 * 2**20, web_private_dirty=60 * 2**20, web_swap=110 * 2**20, top="")
    return Sample(at=at, boot_id="b", units={SCANNER: scanner, KIOSK: kiosk}, system=system)
