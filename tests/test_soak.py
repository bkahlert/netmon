import shlex
import time
from pathlib import Path

import pytest

from sampling import KIOSK, UNITS, parse_duration, read_sample, render_summary, render_table

pytestmark = pytest.mark.soak


class TestSoak:
    def test_both_units_hold_for_the_duration(self, host, request, capfd, report, kiosk_variant):
        duration = parse_duration(request.config.getoption("--soak-duration"))
        interval = parse_duration(request.config.getoption("--soak-interval"))
        kiosk_expected = host.file("/dev/dri").exists
        limits = {unit: host.run(f"cat /sys/fs/cgroup/system.slice/{unit}/memory.max").stdout.strip() or "missing" for unit in UNITS}

        samples = [read_sample(host)]
        deadline = time.monotonic() + duration
        while time.monotonic() < deadline:
            time.sleep(interval)
            samples.append(read_sample(host))
        report.write_text(render_table(samples, limits))

        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        with capfd.disabled():
            reporter.ensure_newline()
            reporter.write_line(f"{render_summary(samples)}; table in {report}")
        first, last = samples[0], samples[-1]
        assert last.boot_id == first.boot_id
        for unit in UNITS:
            if unit == KIOSK and not kiosk_expected:
                continue
            assert last.units[unit].active == "active", unit
            assert last.units[unit].restarts == first.units[unit].restarts, unit
            assert last.units[unit].oom_kills == first.units[unit].oom_kills, unit


@pytest.fixture(scope="module")
def report(request) -> Path:
    target = request.config.getoption("--target")
    path = Path.cwd() / "dist" / ("tier2" if target == "vm" else target) / "soak.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


@pytest.fixture
def kiosk_variant(host, request) -> str | None:
    path = request.config.getoption("--kiosk-conf")
    if path is None:
        return None
    if request.config.getoption("--target") == "ssh":
        pytest.fail("--kiosk-conf rewrites the target's kiosk configuration; use it in the VM only")
    content = Path(path).read_text()
    host.check_output(f"printf '%s' {shlex.quote(content)} > /etc/pihero/kiosk.conf")
    before = host.check_output("systemctl show -p MainPID --value pihero-kiosk.service").strip()
    host.check_output("systemctl restart pihero-kiosk.service")
    for _ in range(45):
        pid = host.check_output("systemctl show -p MainPID --value pihero-kiosk.service").strip()
        if pid not in (before, "0") and host.run("pgrep -x WPEWebProcess").rc == 0:
            return path
        time.sleep(2)
    journal = host.run("journalctl -u pihero-kiosk -b --no-pager -o cat | tail -n 20").stdout
    pytest.fail(f"the kiosk did not come back after the restart with {path}:\n{journal}")
