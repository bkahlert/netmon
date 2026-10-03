from contextlib import ExitStack
from pathlib import Path
from types import SimpleNamespace

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


class TestDevice:
    def test_puts_the_session_on_the_board_in_order_and_records_what_a_crash_would_leave(self, monkeypatch):
        calls, updates = [], []
        install_board(monkeypatch, calls)
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with ExitStack() as cleanup:
            shown = preview_flavors.Device().show(cleanup, settings, lambda **fields: updates.append(fields))

        assert shown == preview_flavors.Shown("http://localhost:8081/", "127.0.0.1:2999")
        assert calls == ["check_kiosk", "session_conf", "open_tunnel", "install", "restore", "close_tunnel"]
        assert updates == [{"tunnel": 77}, {"device": "pi@netmon.local"}]

    def test_restores_the_board_and_closes_the_tunnel_when_the_install_fails(self, monkeypatch):
        calls = []
        install_board(monkeypatch, calls, install_error=RuntimeError("could not put the session on pi@netmon.local"))
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with pytest.raises(RuntimeError, match="could not put the session"):
            with ExitStack() as cleanup:
                preview_flavors.Device().show(cleanup, settings, lambda **fields: None)

        assert calls == ["check_kiosk", "session_conf", "open_tunnel", "install", "restore", "close_tunnel"]

    def test_touches_nothing_on_a_board_without_the_kiosk(self, monkeypatch):
        calls = []
        install_board(monkeypatch, calls, check_error=RuntimeError("pi@netmon.local has no pihero-kiosk"))
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with pytest.raises(RuntimeError, match="no pihero-kiosk"):
            with ExitStack() as cleanup:
                preview_flavors.Device().show(cleanup, settings, lambda **fields: None)

        assert calls == ["check_kiosk"]

    def test_neither_opens_a_tunnel_nor_restarts_the_kiosk_for_a_kiosk_conf_it_cannot_use(self, monkeypatch):
        calls, updates = [], []
        install_board(monkeypatch, calls, conf_error=ValueError("kiosk.conf has no URL= line"))
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with pytest.raises(ValueError, match="kiosk.conf"):
            with ExitStack() as cleanup:
                preview_flavors.Device().show(cleanup, settings, lambda **fields: updates.append(fields))

        assert calls == ["check_kiosk", "session_conf"]
        assert updates == []

    def test_does_not_restart_the_kiosk_when_the_tunnel_does_not_come_up(self, monkeypatch):
        calls = []
        install_board(monkeypatch, calls, tunnel_error=RuntimeError("the ssh tunnel to pi@netmon.local ended"))
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with pytest.raises(RuntimeError, match="tunnel"):
            with ExitStack() as cleanup:
                preview_flavors.Device().show(cleanup, settings, lambda **fields: None)

        assert calls == ["check_kiosk", "session_conf", "open_tunnel"]

    def test_names_the_boards_web_server_for_the_stats(self):
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local:2222"})

        assert preview_flavors.Device().stats_origin(settings) == "http://netmon.local"

    def test_is_picked_for_the_device_flavor(self):
        picked = preview_flavors.flavor_for(Settings.from_environ("device", {"TARGET": "pi@netmon.local"}), Path("session"))

        assert isinstance(picked, preview_flavors.Device)


def install_board(monkeypatch, calls, install_error=None, check_error=None, conf_error=None, tunnel_error=None):
    class FakeBoard:
        def __init__(self, target):
            self.target = target

        def check_kiosk(self):
            calls.append("check_kiosk")
            if check_error:
                raise check_error

        def session_conf(self, broker):
            calls.append("session_conf")
            if conf_error:
                raise conf_error
            return "URL=x\n"

        def open_tunnel(self, broker, dev_port, inspector_port):
            calls.append("open_tunnel")
            if tunnel_error:
                raise tunnel_error
            return SimpleNamespace(pid=77)

        def install(self, conf, tunnel):
            calls.append("install")
            if install_error:
                raise install_error

        def close_tunnel(self, tunnel):
            calls.append("close_tunnel")

        def restore(self):
            calls.append("restore")

    monkeypatch.setattr(preview_flavors.preview_board, "Board", FakeBoard)


def fail_on_update(**fields):
    raise AssertionError(f"unexpected update {fields}")
