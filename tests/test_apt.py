import time
from pathlib import Path

import pytest

from aptprobe import HOOK, TERMINAL, UNIT, classify, command
from sampling import parse_show, read_sample, render_table

pytestmark = pytest.mark.apt
SAMPLE_INTERVAL = 10


class TestAptNextToTheStack:
    def test_update_and_reinstall_complete_with_both_units_running(self, host, request, capfd, report):
        if host.file(HOOK).exists:
            pytest.fail(f"{HOOK} exists on the target and would stop the units around dpkg; move it aside for the probe")
        timeout = request.config.getoption("--apt-timeout")
        package = request.config.getoption("--apt-package")
        host.run(f"systemctl stop {UNIT}; systemctl reset-failed {UNIT}")
        before = read_sample(host)

        started = time.monotonic()
        host.check_output(command(package))
        samples = [before]
        show = {}
        while time.monotonic() - started < timeout:
            time.sleep(SAMPLE_INTERVAL)
            samples.append(read_sample(host))
            show = parse_show(host.run(f"systemctl show -p SubState -p ExecMainStatus -p MemoryPeak -p Result {UNIT}").stdout)
            if show.get("SubState") in TERMINAL:
                break
        elapsed = time.monotonic() - started
        after = read_sample(host)
        host.run(f"systemctl stop {UNIT}; systemctl reset-failed {UNIT}")
        outcome = classify(show.get("SubState", ""), show.get("ExecMainStatus", ""), before.boot_id, after.boot_id, before.units, after.units)

        faults = after.system.pgmajfault - before.system.pgmajfault
        summary = f"apt probe: {outcome}; {elapsed:.0f} s, apt MemoryPeak={show.get('MemoryPeak', 'n/a')}, {faults} major faults, PSI full10 at the end {after.system.pressure_full10}"
        report.write_text(summary + "\n\n" + render_table(samples + [after], {}))
        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        with capfd.disabled():
            reporter.ensure_newline()
            reporter.write_line(f"{summary}; table in {report}")
        assert outcome == "ok", summary


@pytest.fixture(scope="module")
def report(request) -> Path:
    target = request.config.getoption("--target")
    path = Path.cwd() / "dist" / ("tier2" if target == "vm" else target) / "apt-probe.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path
