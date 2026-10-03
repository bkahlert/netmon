from contextlib import ExitStack
from pathlib import Path

import pytest

import preview_flavors
from preview_settings import Settings

pytestmark = pytest.mark.tier0


class TestBrowser:
    def test_shows_the_page_with_its_broker_and_has_no_inspector_of_its_own(self):
        settings = Settings.from_environ("browser", {})

        with ExitStack() as cleanup:
            shown = preview_flavors.Browser().show(cleanup, settings, fail_on_update)

        assert shown == preview_flavors.Shown("http://localhost:8081/?broker.host=localhost&broker.port=8080")

    def test_takes_a_remote_broker_as_it_is(self):
        settings = Settings.from_environ("browser", {"BROKER": "netmon.local:8080"})

        with ExitStack() as cleanup:
            shown = preview_flavors.Browser().show(cleanup, settings, fail_on_update)

        assert shown.page == "http://localhost:8081/?broker.host=netmon.local&broker.port=8080"

    def test_proxies_no_stats(self):
        assert preview_flavors.Browser().stats_origin(Settings.from_environ("browser", {})) is None


class TestFlavorFor:
    @pytest.mark.parametrize("flavor, cls", [("browser", preview_flavors.Browser), ("vm", preview_flavors.Vm)])
    def test_picks_the_class_of_the_flavor(self, flavor, cls):
        picked = preview_flavors.flavor_for(Settings.from_environ(flavor, {}), Path("session"))

        assert isinstance(picked, cls)

    def test_proxies_no_stats_in_the_vm(self):
        assert preview_flavors.Vm(Path("session")).stats_origin(Settings.from_environ("vm", {})) is None


def fail_on_update(**fields):
    raise AssertionError(f"unexpected update {fields}")
