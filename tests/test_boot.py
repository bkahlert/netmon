import json

import pytest

from booted import journal_until, unexpected_recoverable_errors

pytestmark = pytest.mark.boot


class TestProvisioning:
    def test_cloud_init_finished_without_errors(self, host):
        status = json.loads(host.check_output("cloud-init status --long --format json"))

        assert status["errors"] == []
        assert unexpected_recoverable_errors(status) == []
        assert status["status"] == "done", status

    def test_no_unit_failed(self, host):
        failed = host.check_output("systemctl --failed --no-legend --plain").strip()

        assert failed == ""

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


class TestScanner:
    def test_reports_its_memory_after_the_first_scan(self, host, request):
        log = journal_until(host, "completed and published to", attempts=90)
        assert "completed and published to" in log

        show = host.check_output("systemctl show -p MemoryCurrent -p MemoryPeak netmon-scanner.service").splitlines()

        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        reporter.ensure_newline()
        reporter.write_line(f"netmon-scanner after the first scan: {' '.join(show)}")
        assert "MemoryCurrent=[not set]" not in show
        assert any(line.startswith("MemoryCurrent=") for line in show), show
