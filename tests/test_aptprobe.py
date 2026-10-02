import pytest

from aptprobe import UNIT, classify, command
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


def units(scanner_active: str = "active", kiosk_active: str = "active", kiosk_restarts: int = 0, kiosk_oom_kills: int = 0) -> dict[str, UnitSample]:
    def unit(active: str, restarts: int, oom_kills: int) -> UnitSample:
        return UnitSample(active=active, restarts=restarts, current=1, swap_current=1, peak=1, swap_peak=1, anon=1, file=1, oom_kills=oom_kills)

    return {SCANNER: unit(scanner_active, 0, 0), KIOSK: unit(kiosk_active, kiosk_restarts, kiosk_oom_kills)}
