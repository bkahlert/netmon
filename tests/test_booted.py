from pathlib import Path
from types import SimpleNamespace

import pytest
from pihero_testkit.ssh import SshTarget

from booted import exactly, gateway_of, kiosk_conf, tunnel_command, unexpected_recoverable_errors

pytestmark = pytest.mark.tier0


class TestTunnelCommand:
    def test_for_the_vm_uses_its_key_port_and_user_without_connection_sharing(self):
        vm = SimpleNamespace(key=Path("/tmp/vm/pihero-testkit"), port=5022, user="pihero")

        result = tunnel_command(vm, http=18080, ws=18081)

        assert result[:2] == ["ssh", "-N"]
        assert "-L" in result and "127.0.0.1:18080:127.0.0.1:80" in result and "127.0.0.1:18081:127.0.0.1:8080" in result
        assert "-i" in result and "/tmp/vm/pihero-testkit" in result
        assert "-p" in result and "5022" in result
        assert result[-1] == "pihero@127.0.0.1"
        assert "ControlMaster=no" in result and "ControlPath=none" in result

    def test_for_an_ssh_target_uses_the_uri_and_its_port(self):
        result = tunnel_command(SshTarget("pi@netmon.local:2222", []), http=18080, ws=18081)

        assert result[-1] == "pi@netmon.local"
        assert "-p" in result and "2222" in result
        assert "ControlPath=none" in result

    def test_for_an_ssh_target_without_a_port_passes_none(self):
        result = tunnel_command(SshTarget("pi@netmon.local", []), http=18080, ws=18081)

        assert "-p" not in result


class TestGatewayOf:
    def test_returns_the_gateway_of_the_default_route(self):
        result = gateway_of("default via 10.0.2.2 dev eth0 proto dhcp src 10.0.2.15 metric 100\n")

        assert result == "10.0.2.2"

    def test_on_two_default_routes_returns_the_first(self):
        routes = "default via 198.51.100.1 dev eth0 proto dhcp metric 100\ndefault via 198.51.100.1 dev wlan0 proto dhcp metric 600\n"

        result = gateway_of(routes)

        assert result == "198.51.100.1"

    def test_on_no_default_route_raises(self):
        with pytest.raises(ValueError, match="default route"):
            gateway_of("")


class TestExactly:
    def test_matches_the_ip_and_not_a_longer_one(self):
        pattern = exactly("198.51.100.1")

        assert pattern.match("198.51.100.1")
        assert not pattern.match("198.51.100.10")


class TestUnexpectedRecoverableErrors:
    def test_accepts_the_netplan_warning_of_raspberry_pi_os(self):
        status = {"recoverable_errors": {"WARNING": ["Could not find module named cc_netplan_nm_patch"]}}

        result = unexpected_recoverable_errors(status)

        assert result == []

    def test_reports_any_other_warning(self):
        status = {"recoverable_errors": {"WARNING": ["Could not find module named cc_netplan_nm_patch", "Failed to install packages"]}}

        result = unexpected_recoverable_errors(status)

        assert result == ["Failed to install packages"]

    def test_on_no_recoverable_errors_is_empty(self):
        result = unexpected_recoverable_errors({"status": "done"})

        assert result == []


class TestKioskConf:
    def test_reads_assignments_and_strips_double_quotes(self):
        text = 'URL=http://localhost/?a=1&b=2\nCOG_ARGS="--doc-viewer --web-mem-limit=200"\n# a comment\n\nJSC_useFTLJIT=false\n'

        result = kiosk_conf(text)

        assert result == {"URL": "http://localhost/?a=1&b=2", "COG_ARGS": "--doc-viewer --web-mem-limit=200", "JSC_useFTLJIT": "false"}
