import pytest

from booted import exactly, gateway_of, unexpected_recoverable_errors

pytestmark = pytest.mark.tier0


class TestGatewayOf:
    def test_returns_the_gateway_of_the_default_route(self):
        result = gateway_of("default via 10.0.2.2 dev eth0 proto dhcp src 10.0.2.15 metric 100\n")

        assert result == "10.0.2.2"

    def test_on_two_default_routes_returns_the_first(self):
        routes = "default via 192.168.16.1 dev eth0 proto dhcp metric 100\ndefault via 192.168.16.1 dev wlan0 proto dhcp metric 600\n"

        result = gateway_of(routes)

        assert result == "192.168.16.1"

    def test_on_no_default_route_raises(self):
        with pytest.raises(ValueError, match="default route"):
            gateway_of("")


class TestExactly:
    def test_matches_the_ip_and_not_a_longer_one(self):
        pattern = exactly("192.168.16.1")

        assert pattern.match("192.168.16.1")
        assert not pattern.match("192.168.16.10")


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
