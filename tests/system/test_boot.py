import json
import shlex
import time
from pathlib import Path

import pytest
from pihero_testkit.ssh import SshTarget

from netmon_dev.system.booted import journal_until, kiosk_conf, unexpected_recoverable_errors
from netmon_dev.system.sampling import subscribed

pytestmark = pytest.mark.boot
WEB_PROCESS_SAMPLES = 20


class TestProvisioning:
    def test_cloud_init_finished_without_errors(self, host):
        # cloud-init exits 2 for "degraded done", which the recoverable-errors filter judges; the JSON is read either way.
        status = json.loads(host.run("cloud-init status --long --format json").stdout)

        assert status["errors"] == []
        assert unexpected_recoverable_errors(status) == []
        assert status["status"] == "done", status

    def test_no_unit_failed(self, host):
        failed = host.check_output("systemctl --failed --no-legend --plain").strip()

        assert failed == ""

    def test_the_journal_is_configured_to_stay_on_disk(self, host):
        config = host.check_output("systemd-analyze cat-config systemd/journald.conf")

        assert "Storage=persistent" in config.splitlines()

    def test_the_bootconfig_lines_reached_the_kernel(self, host):
        cmdline = host.file("/proc/cmdline").content_string.split()

        assert "video=HDMI-A-1:800x480M@60e" in cmdline
        assert "cgroup_enable=memory" in cmdline


class TestKiosk:
    def test_is_skipped_by_its_condition_without_a_display_adapter(self, host):
        if host.file("/dev/dri").exists:
            pytest.skip("has a display adapter")

        states = host.check_output("systemctl show --property=ActiveState --property=ConditionResult --value pihero-kiosk.service").split()

        assert states == ["inactive", "no"]

    def test_runs_on_the_display_without_a_restart(self, host, target):
        if isinstance(target, SshTarget):
            pytest.skip("a board's restart count spans its uptime")
        if not host.file("/dev/dri").exists:
            pytest.skip("no display adapter")

        show = host.check_output("systemctl show -p ActiveState -p NRestarts pihero-kiosk.service")

        properties = dict(line.split("=", 1) for line in show.splitlines())
        assert properties == {"ActiveState": "active", "NRestarts": "0"}

    def test_runs_cog_with_the_configured_arguments(self, host):
        if not host.file("/dev/dri").exists:
            pytest.skip("no display adapter")
        conf = kiosk_conf(host.file("/etc/pihero/kiosk.conf").content_string)
        pid = host.check_output("pgrep -x cog").split()[0]

        cmdline = host.check_output(f"tr '\\0' '\\n' < /proc/{pid}/cmdline").splitlines()

        assert set(shlex.split(conf["COG_ARGS"])) <= set(cmdline), cmdline

    def test_the_web_process_sees_the_webkit_variables(self, host):
        if not host.file("/dev/dri").exists:
            pytest.skip("no display adapter")
        conf = kiosk_conf(host.file("/etc/pihero/kiosk.conf").content_string)
        expected = {f"{name}={value}" for name, value in conf.items() if name.startswith(("JSC_", "WEBKIT_"))}
        pid = host.check_output("pgrep -x WPEWebProcess").split()[0]

        environ = host.check_output(f"tr '\\0' '\\n' < /proc/{pid}/environ").splitlines()

        assert expected, conf
        assert expected <= set(environ), sorted(environ)

    def test_is_pictured_after_the_first_scan(self, host, target, request, capfd):
        if getattr(target, "display", None) is None:
            pytest.skip("no virtual display")
        log = journal_until(host, "completed and published to", attempts=90)
        assert "completed and published to" in log
        wait_until_the_page_shows_the_kiosks_load(target)

        picture = target.screenshot(Path.cwd() / "dist" / "tier2" / "kiosk.png")
        show = host.check_output("systemctl show -p MemoryCurrent -p MemoryPeak pihero-kiosk.service").splitlines()

        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        with capfd.disabled():
            reporter.ensure_newline()
            reporter.write_line(f"pihero-kiosk after the first scan: {' '.join(show)}")
        assert png_size(picture) == (800, 480)


class TestScanner:
    def test_reports_its_memory_after_the_first_scan(self, host, request, capfd):
        log = journal_until(host, "completed and published to", attempts=90)
        assert "completed and published to" in log

        show = host.check_output("systemctl show -p MemoryCurrent -p MemoryPeak netmon-scanner.service").splitlines()

        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        with capfd.disabled():
            reporter.ensure_newline()
            reporter.write_line(f"netmon-scanner after the first scan: {' '.join(show)}")
        assert "MemoryCurrent=[not set]" not in show
        assert any(line.startswith("MemoryCurrent=") for line in show), show


def png_size(path: Path) -> tuple[int, int]:
    header = path.read_bytes()[:24]
    assert header[:8] == b"\x89PNG\r\n\x1a\n"
    return int.from_bytes(header[16:20], "big"), int.from_bytes(header[20:24], "big")


def wait_until_the_page_shows_the_kiosks_load(target) -> None:
    with subscribed(target) as stream:
        if all(stream.next().system.web_pid is None for _ in range(WEB_PROCESS_SAMPLES)):
            pytest.fail(f"none of {WEB_PROCESS_SAMPLES} metrics samples named the web process")
        stream.next()
    time.sleep(1)
