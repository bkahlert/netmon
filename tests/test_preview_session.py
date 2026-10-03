import http.server
import re
import socket
import struct
import sys
import threading
import time
import urllib.request
from types import SimpleNamespace

import pytest

import preview_device
import preview_kiosk
import preview_session

MAC_ONLY = pytest.mark.skipif(sys.platform != "darwin", reason="the window is a macOS window")


@pytest.mark.tier0
class TestSessionStop:
    def test_removes_the_directory_of_a_session_that_never_started(self, tmp_path):
        directory = tmp_path / "session"
        directory.mkdir()
        session = preview_session.Session(preview_device.Layer(tmp_path / "r", tmp_path / "b"), directory)

        session.stop()

        assert not directory.exists()


@pytest.mark.tier0
class TestSessionFailure:
    def test_keeps_the_serial_log_of_a_kiosk_that_did_not_load(self, tmp_path):
        directory = tmp_path / "session"
        directory.mkdir()
        (directory / "serial.log").write_text("boot messages")
        session = preview_session.Session(preview_device.Layer(tmp_path / "r", tmp_path / "b"), directory)
        session.vm = SimpleNamespace(ssh=lambda command: SimpleNamespace(stdout=""), serial_log=directory / "serial.log", process=None)

        with pytest.raises(TimeoutError, match=re.escape(str(tmp_path / "serial.log"))):
            session.restart_kiosk(timeout=0)
        session.stop()

        assert (tmp_path / "serial.log").read_text() == "boot messages"
        assert not directory.exists()


@pytest.mark.preview
@MAC_ONLY
class TestSession:
    def test_keeps_the_guest_at_800_by_480_whatever_the_window_does(self, session):
        resized = session.resize_window(1000, 700)
        session.restart_kiosk()

        size = picture_size(session)

        assert (resized, size) == (True, (800, 480))

    def test_tunnels_the_inspector_of_a_page_served_from_the_macs_loopback(self, session):
        port = free_port()
        session.open_tunnel(port, 2999)

        listing = wait_for_listing(port)

        assert preview_kiosk.inspector_url(listing, f"127.0.0.1:{port}") is not None


@pytest.fixture(scope="module")
def session(tmp_path_factory, page):
    layer = preview_device.ensure_layer()
    started = preview_session.Session(layer, tmp_path_factory.mktemp("session"))
    started.start()
    started.place_window()
    started.configure_kiosk(f"http://10.0.2.2:{page}/", 2999)
    yield started
    started.stop()


@pytest.fixture(scope="module")
def page(tmp_path_factory):
    directory = tmp_path_factory.mktemp("page")
    (directory / "index.html").write_text("<h1>preview</h1>")
    handler = lambda *args, **kwargs: http.server.SimpleHTTPRequestHandler(*args, directory=str(directory), **kwargs)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    yield server.server_address[1]
    server.shutdown()


def picture_size(session) -> tuple[int, int]:
    png = session.directory / "picture.png"
    session.vm.screenshot(png)
    return struct.unpack(">II", png.read_bytes()[16:24])


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def wait_for_listing(port: int) -> str:
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            listing = urllib.request.urlopen(f"http://127.0.0.1:{port}/", timeout=3).read().decode()
            if "socket/" in listing:
                return listing
        except OSError:
            pass
        time.sleep(1)
    raise AssertionError("the inspector's page list named no target within 60 seconds")
