import pytest

import preview_broker
from preview_settings import Settings
from scan_fixtures import Scan

pytestmark = pytest.mark.tier0


class TestFromEnviron:
    def test_defaults_to_the_fixture_the_standard_scan_and_safari(self):
        settings = Settings.from_environ("vm", {})

        assert settings == Settings("vm", Scan(14, 39, 1), preview_broker.Broker(preview_broker.FIXTURE, "localhost", 8080), "Safari", None)

    def test_reads_every_variable_of_the_device_flavor(self):
        settings = Settings.from_environ("device", {"SCAN": "3+1x2", "BROKER": "device", "INSPECT": "0", "TARGET": "pi@netmon.local:2222"})

        assert settings == Settings("device", Scan(3, 1, 2), preview_broker.Broker(preview_broker.DEVICE, "127.0.0.1", 8080), None, "pi@netmon.local:2222")

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_takes_empty_variables_for_unset_ones(self, flavor):
        settings = Settings.from_environ(flavor, {"BROKER": "", "TARGET": "", "SCAN": ""})

        assert settings == Settings(flavor, Scan(14, 39, 1), preview_broker.Broker(preview_broker.FIXTURE, "localhost", 8080), "Safari", None)

    @pytest.mark.parametrize("environ, message", [({"SCAN": "x"}, "SCAN must be"), ({"BROKER": "x"}, "BROKER must be fixture, device or HOST:PORT")])
    def test_names_the_malformed_variable(self, environ, message):
        with pytest.raises(ValueError, match=message):
            Settings.from_environ("vm", environ)

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_keeps_the_boards_broker_for_the_device(self, flavor):
        with pytest.raises(ValueError, match="BROKER=device is only for preview-device"):
            Settings.from_environ(flavor, {"BROKER": "device"})

    def test_needs_a_target_for_the_device(self):
        with pytest.raises(ValueError, match="preview-device needs TARGET=user@host"):
            Settings.from_environ("device", {})

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_refuses_a_target_elsewhere(self, flavor):
        with pytest.raises(ValueError, match="TARGET is only for preview-device"):
            Settings.from_environ(flavor, {"TARGET": "pi@netmon.local"})

    def test_refuses_an_unknown_flavor(self):
        with pytest.raises(ValueError, match="flavor must be browser, vm or device"):
            Settings.from_environ("tv", {})
