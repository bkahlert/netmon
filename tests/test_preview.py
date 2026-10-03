import json
import os

import pytest

import preview
import preview_broker
import preview_device
import preview_session
from scan_fixtures import Scan


@pytest.mark.tier0
class TestSettings:
    def test_defaults_to_the_managed_broker_the_standard_fixture_and_safari(self):
        settings = preview.Settings.from_environ({})

        assert settings == preview.Settings(Scan(14, 39, 1), preview_broker.Broker("localhost", 8080, managed=True), "Safari")

    def test_reads_all_three_variables(self):
        settings = preview.Settings.from_environ({"SCAN": "3+1x2", "BROKER": "netmon.local:8080", "INSPECT": "0"})

        assert settings == preview.Settings(Scan(3, 1, 2), preview_broker.Broker("netmon.local", 8080, managed=False), None)

    @pytest.mark.parametrize("environ, message", [({"SCAN": "x"}, "SCAN must be"), ({"BROKER": "x"}, "BROKER must be")])
    def test_names_the_malformed_variable(self, environ, message):
        with pytest.raises(ValueError, match=message):
            preview.Settings.from_environ(environ)


@pytest.mark.tier0
class TestStaleActions:
    def test_refuses_to_start_next_to_a_running_preview(self):
        record = {"owner": 100}

        with pytest.raises(preview.AlreadyRunning, match="100"):
            preview.stale_actions(record, commands({100: "python tests/preview.py"}))

    def test_ends_what_a_killed_preview_left_behind(self):
        record = {"owner": 100, "qemu": 200, "gradle": 300, "broker": True}

        actions = preview.stale_actions(record, commands({200: "qemu-system-aarch64 -M virt", 300: "/bin/sh ./gradlew --console=plain jsBrowserDevelopmentRun"}))

        assert actions == [("terminate", 200), ("terminate-group", 300), ("stop-broker", None)]

    def test_leaves_alone_a_process_that_reuses_the_recorded_id(self):
        record = {"owner": 100, "qemu": 200, "gradle": 300}

        actions = preview.stale_actions(record, commands({200: "/usr/bin/vim notes.txt", 300: "firefox"}))

        assert actions == []

    def test_does_nothing_for_an_empty_record(self):
        assert preview.stale_actions({}, commands({})) == []


@pytest.mark.tier0
class TestWaitForInspector:
    def test_returns_the_inspector_of_the_first_target_once_the_list_names_one(self):
        listings = iter(["", EMPTY_LISTING, LISTING])

        found = preview.wait_for_inspector("127.0.0.1:2999", fetch=lambda address: next(listings), sleep=lambda s: None)

        assert found == "http://127.0.0.1:2999/Main.html?ws=127.0.0.1:2999/socket/1/1/WebPage"

    def test_falls_back_to_the_list_itself_on_a_list_without_a_target(self):
        now = iter(range(0, 1000, 10))

        found = preview.wait_for_inspector("127.0.0.1:2999", timeout=30, fetch=lambda address: EMPTY_LISTING, sleep=lambda s: None, clock=lambda: next(now))

        assert found == "http://127.0.0.1:2999/"


@pytest.mark.tier0
class TestWaitUntilGone:
    def test_returns_once_every_process_is_gone(self):
        alive = {200: 2, 300: 1}

        def command_of(pid):
            alive[pid] -= 1
            return "qemu" if alive[pid] > 0 else None

        preview.wait_until_gone([200, 300], command_of, sleep=lambda s: None)

        assert alive == {200: 0, 300: 0}

    def test_gives_up_on_a_process_that_stays(self):
        now = iter(range(0, 1000, 10))

        with pytest.raises(TimeoutError, match="200"):
            preview.wait_until_gone([200], lambda pid: "qemu", timeout=30, sleep=lambda s: None, clock=lambda: next(now))


@pytest.mark.tier0
class TestDevServerPort:
    def test_is_the_port_the_pages_use(self):
        assert preview.DEV_PORT == 8081


@pytest.mark.preview
class TestClaim:
    def test_ends_the_qemu_a_killed_preview_left_behind(self, tmp_path, monkeypatch):
        monkeypatch.setattr(preview, "STATE", tmp_path)
        monkeypatch.setattr(preview, "RECORD", tmp_path / "session.json")
        monkeypatch.setattr(preview, "SESSION_DIR", tmp_path / "session")
        session = preview_session.Session(preview_device.ensure_layer(), tmp_path / "session", window=False)
        vm = session.start()
        try:
            preview.RECORD.write_text(json.dumps({"owner": 2**22 + 777, "qemu": vm.process.pid}))

            preview.claim()

            vm.process.wait(timeout=30)
            record = json.loads(preview.RECORD.read_text())
        finally:
            session.stop()
        assert vm.process.returncode is not None
        assert record == {"owner": os.getpid()}


def commands(by_pid):
    return lambda pid: by_pid.get(pid)


LISTING = """<html><body><div id='targetlist'><table><tbody><tr><td class="input"><input type="button" value="Inspect" onclick="window.open('Main.html?ws=' + window.location.host + '/socket/1/1/WebPage', '_blank');"></td></tr></tbody></table></div></body></html>"""
EMPTY_LISTING = "<html><body><div id='targetlist'><table><tbody></tbody></table></div></body></html>"
