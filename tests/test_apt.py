import time
from pathlib import Path

import pytest

from aptprobe import HOOK, PEAK_FILE, SAMPLER, TERMINAL, UNIT, classify, command, parse_anon_peak, sampler_command
from sampling import KIOSK, UNITS, na, parse_show, read_sample, render_table

pytestmark = pytest.mark.apt
SAMPLE_INTERVAL = 10


class TestAptNextToTheStack:
    def test_update_and_reinstall_complete_with_both_units_running(self, host, request, capfd, report):
        if host.file(HOOK).exists:
            pytest.fail(f"{HOOK} exists on the target and would stop the units around dpkg; move it aside for the probe")
        timeout = request.config.getoption("--apt-timeout")
        package = request.config.getoption("--apt-package")
        host.run(f"systemctl stop {UNIT} {SAMPLER}; systemctl reset-failed {UNIT} {SAMPLER}; rm -f {PEAK_FILE}")
        before = read_sample(host)
        kiosk_expected = host.file("/dev/dri").exists
        for unit in UNITS:
            if unit == KIOSK and not kiosk_expected:
                continue
            if before.units[unit].active != "active":
                pytest.fail(f"{unit} is {before.units[unit].active} before the run; the probe needs both units running")

        started = time.monotonic()
        host.check_output(sampler_command(timeout))
        host.check_output(command(package))
        samples = [before]
        show = {}
        while time.monotonic() - started < timeout:
            time.sleep(SAMPLE_INTERVAL)
            try:
                samples.append(read_sample(host))
            except ConnectionError:
                continue
            show = parse_show(host.run(f"systemctl show -p SubState -p ExecMainStatus -p MemoryPeak -p Result {UNIT}").stdout)
            if show.get("SubState") in TERMINAL:
                break
        elapsed = time.monotonic() - started
        try:
            after = read_sample(host)
        except ConnectionError:
            after = None
        anon_peak = parse_anon_peak(host.run(f"cat {PEAK_FILE}").stdout)
        host.run(f"systemctl stop {UNIT} {SAMPLER}; systemctl reset-failed {UNIT} {SAMPLER}")
        boot_after, units_after = (after.boot_id, after.units) if after is not None else ("", before.units)
        outcome = classify(show.get("SubState", ""), show.get("ExecMainStatus", ""), before.boot_id, boot_after, before.units, units_after)

        if after is not None:
            samples.append(after)
        faults = samples[-1].system.pgmajfault - before.system.pgmajfault
        pressure = na(max((s.system.pressure_full10 for s in samples if s.system.pressure_full10 is not None), default=None))
        peaks = f"apt MemoryPeak={show.get('MemoryPeak', 'n/a')}, apt anon peak={'n/a' if anon_peak is None else anon_peak}"
        summary = f"apt probe: {outcome}; {elapsed:.0f} s, {peaks}, {faults} major faults, PSI full10 peak {pressure}"
        report.write_text(summary + "\n\n" + render_table(samples, {}))
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
