import pytest

from aptprobe import PEAK_FILE, SAMPLER, STAT_FILE, UNIT, classify, command, parse_anon_peak, sampler_command
from sampling import KIOSK, SCANNER, UnitSample

pytestmark = pytest.mark.tier0


class TestCommand:
    def test_runs_update_and_the_reinstall_in_an_accounted_transient_unit(self):
        result = command("netmon-display")

        assert result.startswith(f"systemd-run --unit={UNIT} ")
        assert "-p MemoryAccounting=yes" in result and "-p RemainAfterExit=yes" in result
        assert "apt-get update && " in result and "apt-get install -y --reinstall netmon-display" in result


class TestClassify:
    def test_ok_when_apt_exited_zero_and_nothing_else_changed(self):
        assert classify("exited", "0", "boot-1", "boot-1", units(), units()) == "ok"

    def test_a_different_boot_id_is_a_reset(self):
        assert classify("exited", "0", "boot-1", "boot-2", units(), units()) == "the target rebooted during the run"

    def test_an_unreachable_target_is_reported_as_such(self):
        assert classify("", "", "boot-1", "", units(), units()) == "the target is unreachable after the run"

    def test_a_unit_still_running_apt_is_a_timeout(self):
        assert classify("running", "", "boot-1", "boot-1", units(), units()).startswith("apt did not finish")

    def test_apts_exit_status_is_named(self):
        assert classify("exited", "100", "boot-1", "boot-1", units(), units()) == "apt exited with 100"

    def test_a_restarted_or_stopped_unit_is_named(self):
        assert classify("exited", "0", "boot-1", "boot-1", units(), units(kiosk_restarts=1)) == f"{KIOSK} restarted during the run"
        assert classify("exited", "0", "boot-1", "boot-1", units(), units(scanner_active="inactive")) == f"{SCANNER} is inactive after the run"

    def test_a_unit_that_was_inactive_before_and_after_is_named(self):
        result = classify("exited", "0", "boot-1", "boot-1", units(kiosk_active="failed"), units(kiosk_active="failed"))

        assert result == f"{KIOSK} is failed after the run"

    def test_an_oom_kill_during_the_run_is_named(self):
        result = classify("exited", "0", "boot-1", "boot-1", units(), units(kiosk_oom_kills=1))

        assert result == f"{KIOSK} had an oom kill during the run"


class TestSamplerCommand:
    def test_starts_a_transient_unit_that_samples_the_probe_units_anonymous_memory(self):
        result = sampler_command(300)

        assert result.startswith(f"systemd-run --unit={SAMPLER} --quiet sh -c '")
        assert STAT_FILE in result and f"/sys/fs/cgroup/system.slice/{UNIT}.service/memory.stat" == STAT_FILE
        assert "^anon " in result

    def test_writes_the_maximum_to_the_peak_file_every_iteration_for_at_most_the_timeout(self):
        result = sampler_command(300)

        assert f"> {PEAK_FILE}" in result and PEAK_FILE == "/run/netmon-apt-anon-peak"
        assert "+ 300" in result and "sleep 0.5" in result

    def test_a_missing_stat_file_counts_as_zero(self):
        result = sampler_command(300)

        assert "2>/dev/null" in result and "${anon:-0}" in result


class TestParseAnonPeak:
    def test_reads_the_byte_count(self):
        assert parse_anon_peak("123456789\n") == 123456789

    def test_empty_content_is_none(self):
        assert parse_anon_peak("") is None
        assert parse_anon_peak("\n") is None

    def test_a_peak_of_zero_is_zero(self):
        assert parse_anon_peak("0\n") == 0


def units(scanner_active: str = "active", kiosk_active: str = "active", kiosk_restarts: int = 0, kiosk_oom_kills: int = 0) -> dict[str, UnitSample]:
    def unit(active: str, restarts: int, oom_kills: int) -> UnitSample:
        return UnitSample(active=active, restarts=restarts, current=1, swap_current=1, peak=1, swap_peak=1, anon=1, file=1, oom_kills=oom_kills, cpu_seconds=1)

    return {SCANNER: unit(scanner_active, 0, 0), KIOSK: unit(kiosk_active, kiosk_restarts, kiosk_oom_kills)}
