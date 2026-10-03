"""The places a preview shows the page: a browser tab, the kiosk in a VM window and, below, the kiosk of a real board."""
from contextlib import ExitStack
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Protocol

import preview_board
import preview_dev_server
import preview_device
import preview_kiosk
import preview_session
from preview_settings import Settings

DEV_PORT = preview_dev_server.PORT


@dataclass(frozen=True)
class Shown:
    """What a flavor put up: the page's URL as the Mac's browser loads it, the kiosk's Web Inspector address (None where the page's own tools inspect it) and a check the session asks while it runs."""

    page: str
    inspector: str | None = None
    watch: Callable[[], str | None] | None = field(default=None, compare=False)


class Flavor(Protocol):
    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown: ...

    def stats_origin(self, settings: Settings) -> str | None: ...


class Browser:
    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown:
        return Shown(preview_kiosk.browser_url(DEV_PORT, settings.broker.host, settings.broker.port))

    def stats_origin(self, settings: Settings) -> str | None:
        return None


class Vm:
    def __init__(self, session_dir: Path):
        self.session_dir = session_dir

    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown:
        layer = preview_device.ensure_layer()
        session = preview_session.Session(layer, self.session_dir)
        cleanup.callback(session.stop)
        session.start(on_qemu=lambda pid: update(qemu=pid))
        session.place_window()
        session.configure_kiosk(preview_kiosk.page_url(DEV_PORT, settings.broker.host, settings.broker.port), preview_kiosk.INSPECTOR_PORT)
        session.open_tunnel(preview_kiosk.INSPECTOR_PORT, preview_kiosk.INSPECTOR_PORT)
        return Shown(f"http://localhost:{DEV_PORT}/", f"127.0.0.1:{preview_kiosk.INSPECTOR_PORT}")

    def stats_origin(self, settings: Settings) -> str | None:
        return None


class Device:
    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown:
        board = preview_board.Board(settings.target)
        board.check_kiosk()
        conf = board.session_conf(settings.broker)
        tunnel = board.open_tunnel(settings.broker, DEV_PORT, preview_kiosk.INSPECTOR_PORT)
        cleanup.callback(board.close_tunnel, tunnel)
        update(tunnel=tunnel.pid)
        cleanup.callback(board.restore)
        update(device=settings.target)
        board.install(conf, tunnel)
        return Shown(f"http://localhost:{DEV_PORT}/", f"127.0.0.1:{preview_kiosk.INSPECTOR_PORT}", lambda: board.tunnel_problem(tunnel))

    def stats_origin(self, settings: Settings) -> str | None:
        return preview_board.stats_origin(settings.target)


def flavor_for(settings: Settings, session_dir: Path) -> Flavor:
    if settings.flavor == "browser":
        return Browser()
    if settings.flavor == "vm":
        return Vm(session_dir)
    return Device()
