"""make preview-browser, preview-vm and preview-device: the broker, the dev server and the page in a browser, a VM's kiosk or a board's kiosk, until Ctrl-C."""
import argparse
import json
import os
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
from contextlib import ExitStack
from pathlib import Path
from typing import Callable

import preview_board
import preview_broker
import preview_dev_server
import preview_flavors
import preview_kiosk
import preview_process
import preview_settings

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / "dist" / "preview"
RECORD = STATE / "session.json"
SESSION_DIR = STATE / "session"
DEV_PORT = preview_dev_server.PORT


class AlreadyRunning(RuntimeError):
    pass


def stale_actions(record: dict, command_of: Callable[[int], str | None]) -> list[tuple[str, int | str | None]]:
    """Returns what a killed preview left behind that must be ended; raises AlreadyRunning if its owner is still alive."""
    owner = record.get("owner")
    if owner and "preview.py" in (command_of(owner) or ""):
        raise AlreadyRunning(f"a preview is already running (process {owner}); end it with Ctrl-C first")
    actions: list[tuple[str, int | str | None]] = []
    qemu, gradle, tunnel = record.get("qemu"), record.get("gradle"), record.get("tunnel")
    if qemu and "qemu-system" in (command_of(qemu) or ""):
        actions.append(("terminate", qemu))
    if gradle and "gradle" in (command_of(gradle) or "").lower():
        actions.append(("terminate-group", gradle))
    if tunnel and "ssh -N" in (command_of(tunnel) or ""):
        actions.append(("terminate", tunnel))
    if record.get("device"):
        actions.append(("restore-device", record["device"]))
    if record.get("broker"):
        actions.append(("stop-broker", None))
    return actions


def carry_out(actions: list[tuple[str, int | str | None]], command_of: Callable[[int], str | None] = preview_process.command_of) -> None:
    ended = []
    for action, subject in actions:
        try:
            if action == "terminate":
                os.kill(subject, signal.SIGTERM)
                ended.append(subject)
            elif action == "terminate-group":
                os.killpg(os.getpgid(subject), signal.SIGTERM)
                ended.append(subject)
            elif action == "restore-device":
                preview_board.Board(subject).restore()
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


def forget() -> None:
    """Deletes the record, keeping only a board that still runs the session so the next start retries its restore."""
    device = json.loads(RECORD.read_text()).get("device") if RECORD.exists() else None
    if device:
        RECORD.write_text(json.dumps({"device": device}))
    else:
        RECORD.unlink(missing_ok=True)


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


def run(settings: preview_settings.Settings, flavor: preview_flavors.Flavor) -> int:
    preview_process.raise_on_sigterm()
    claim()
    with ExitStack() as cleanup:
        cleanup.callback(forget)
        if settings.broker.managed:
            preview_broker.ensure(settings.broker, settings.scan)
            cleanup.callback(preview_broker.stop)
            update(broker=True)
        print(f"dev server: Gradle on port {DEV_PORT}, log in {preview_dev_server.LOG}", file=sys.stderr, flush=True)
        server = preview_dev_server.ensure(flavor.stats_origin(settings))
        cleanup.callback(preview_dev_server.stop, server)
        update(gradle=server.pid)
        shown = flavor.show(cleanup, settings, update)
        opened = wait_for_inspector(shown.inspector) if shown.inspector else shown.page
        if settings.inspect:
            subprocess.run(preview_kiosk.open_command(settings.inspect, opened), check=False)
        inspector = f"\n  inspector  http://{shown.inspector}/" if shown.inspector else ""
        ready = f"preview ready ({settings.flavor})\n  page       {shown.page}\n  broker     {settings.broker.describe()}{inspector}"
        print(f"{ready}\nCtrl-C ends it.", file=sys.stderr, flush=True)
        preview_process.until_interrupted(shown.watch)
    return 0


def main(argv: list[str] | None = None, environ=os.environ) -> int:
    parser = argparse.ArgumentParser(prog="preview.py", description=__doc__)
    parser.add_argument("--on", required=True, choices=preview_settings.FLAVORS, help="where to show the page")
    flavor_name = parser.parse_args(argv).on
    try:
        settings = preview_settings.Settings.from_environ(flavor_name, environ)
        return run(settings, preview_flavors.flavor_for(settings, SESSION_DIR))
    except (ValueError, AlreadyRunning, RuntimeError, TimeoutError) as error:
        print(error, file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
