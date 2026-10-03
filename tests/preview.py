"""make preview: the broker, the dev server and the kiosk in a VM window, until Ctrl-C."""
import json
import os
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
from contextlib import ExitStack
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import preview_broker
import preview_dev_server
import preview_device
import preview_kiosk
import preview_process
import preview_session
import scan_fixtures

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / "dist" / "preview"
RECORD = STATE / "session.json"
SESSION_DIR = STATE / "session"
DEV_PORT = preview_dev_server.PORT
INSPECTOR_PORT = 2999


class AlreadyRunning(RuntimeError):
    pass


@dataclass(frozen=True)
class Settings:
    scan: scan_fixtures.Scan
    broker: preview_broker.Broker
    inspect: str | None

    @staticmethod
    def from_environ(environ) -> "Settings":
        return Settings(
            scan_fixtures.parse_scan(environ.get("SCAN") or "14+39"),
            preview_broker.parse_broker(environ.get("BROKER")),
            preview_kiosk.inspect_app(environ.get("INSPECT", "Safari")),
        )


def stale_actions(record: dict, command_of: Callable[[int], str | None]) -> list[tuple[str, int | None]]:
    """Returns what a killed preview left behind that must be ended; raises AlreadyRunning if its owner is still alive."""
    owner = record.get("owner")
    if owner and "preview.py" in (command_of(owner) or ""):
        raise AlreadyRunning(f"a preview is already running (process {owner}); end it with Ctrl-C first")
    actions: list[tuple[str, int | None]] = []
    qemu, gradle = record.get("qemu"), record.get("gradle")
    if qemu and "qemu-system" in (command_of(qemu) or ""):
        actions.append(("terminate", qemu))
    if gradle and "gradle" in (command_of(gradle) or "").lower():
        actions.append(("terminate-group", gradle))
    if record.get("broker"):
        actions.append(("stop-broker", None))
    return actions


def carry_out(actions: list[tuple[str, int | None]], command_of: Callable[[int], str | None] = preview_process.command_of) -> None:
    ended = []
    for action, pid in actions:
        try:
            if action == "terminate":
                os.kill(pid, signal.SIGTERM)
                ended.append(pid)
            elif action == "terminate-group":
                os.killpg(os.getpgid(pid), signal.SIGTERM)
                ended.append(pid)
            elif action == "stop-broker":
                preview_broker.stop()
        except ProcessLookupError:
            pass
    wait_until_gone(ended, command_of)


def wait_until_gone(pids: list[int], command_of: Callable[[int], str | None], timeout: float = 30, sleep=time.sleep, clock=time.monotonic) -> None:
    """Returns once none of `pids` runs any more, so the next start does not adopt a server that is still shutting down."""
    deadline = clock() + timeout
    running = [pid for pid in pids if command_of(pid)]
    while running:
        if clock() >= deadline:
            raise TimeoutError(f"process {running[0]} of a killed preview did not end within {timeout:g} s")
        sleep(0.5)
        running = [pid for pid in running if command_of(pid)]


def claim() -> None:
    """Ends what a killed preview left behind, then records this process as the owner; raises AlreadyRunning next to a live preview."""
    STATE.mkdir(parents=True, exist_ok=True)
    if RECORD.exists():
        try:
            record = json.loads(RECORD.read_text())
        except ValueError:
            record = {}
        carry_out(stale_actions(record, preview_process.command_of))
        shutil.rmtree(SESSION_DIR, ignore_errors=True)
    RECORD.write_text(json.dumps({"owner": os.getpid()}))


def update(**fields) -> None:
    record = json.loads(RECORD.read_text())
    record.update(fields)
    RECORD.write_text(json.dumps(record))


def fetch_listing(address: str) -> str:
    try:
        return urllib.request.urlopen(f"http://{address}/", timeout=2).read().decode()
    except OSError:
        return ""


def wait_for_inspector(address: str, timeout: float = 30, fetch=fetch_listing, sleep=time.sleep, clock=time.monotonic) -> str:
    """Returns the Web Inspector's address once its page list names a target, else the list's own address after `timeout` seconds."""
    deadline = clock() + timeout
    while clock() < deadline:
        found = preview_kiosk.inspector_url(fetch(address), address)
        if found:
            return found
        sleep(1)
    return f"http://{address}/"


def run(settings: Settings) -> int:
    preview_process.raise_on_sigterm()
    claim()
    with ExitStack() as cleanup:
        cleanup.callback(RECORD.unlink, missing_ok=True)
        if settings.broker.managed:
            preview_broker.ensure(settings.broker, settings.scan)
            cleanup.callback(preview_broker.stop)
            update(broker=True)
        print(f"dev server: Gradle on port {DEV_PORT}, log in {preview_dev_server.LOG}", file=sys.stderr, flush=True)
        server = preview_dev_server.ensure()
        cleanup.callback(preview_dev_server.stop, server)
        update(gradle=server.pid)
        layer = preview_device.ensure_layer()
        session = preview_session.Session(layer, SESSION_DIR)
        cleanup.callback(session.stop)
        session.start(on_qemu=lambda pid: update(qemu=pid))
        session.place_window()
        session.configure_kiosk(preview_kiosk.page_url(DEV_PORT, settings.broker.host, settings.broker.port), INSPECTOR_PORT)
        session.open_tunnel(INSPECTOR_PORT, INSPECTOR_PORT)
        inspector = wait_for_inspector(f"127.0.0.1:{INSPECTOR_PORT}")
        if settings.inspect:
            subprocess.run(preview_kiosk.open_command(settings.inspect, inspector), check=False)
        ready = f"preview ready\n  page       http://localhost:{DEV_PORT}/\n  broker     {settings.broker.address}\n  inspector  http://127.0.0.1:{INSPECTOR_PORT}/"
        print(f"{ready}\nCtrl-C ends it.", file=sys.stderr, flush=True)
        preview_process.until_interrupted()
    return 0


def main(environ=os.environ) -> int:
    try:
        return run(Settings.from_environ(environ))
    except (ValueError, AlreadyRunning, RuntimeError, TimeoutError) as error:
        print(error, file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
