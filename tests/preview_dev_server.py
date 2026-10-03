"""Gradle's dev server for the display, on the port the preview's pages and the kiosk use."""
import os
import signal
import subprocess
import time
from pathlib import Path

import preview_process

ROOT = Path(__file__).resolve().parents[1]
PORT = 8081
LOG = ROOT / "dist" / "preview" / "gradle.log"


def ensure() -> subprocess.Popen | None:
    """Starts the dev server unless something already answers on its port; returns the process it started, else None."""
    if preview_process.answers("127.0.0.1", PORT):
        return None
    process = start()
    try:
        wait_until_serving(process)
    except BaseException:
        stop(process)
        raise
    return process


def start() -> subprocess.Popen:
    LOG.parent.mkdir(parents=True, exist_ok=True)
    return subprocess.Popen(
        ["./gradlew", "--console=plain", "jsBrowserDevelopmentRun", "--continuous"],
        cwd=ROOT, stdout=LOG.open("w"), stderr=subprocess.STDOUT, start_new_session=True,
    )


def wait_until_serving(process, timeout: float = 900, answers=preview_process.answers, sleep=time.sleep, clock=time.monotonic) -> None:
    deadline = clock() + timeout
    while clock() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Gradle's dev server exited with status {process.returncode}; see {LOG}")
        if answers("127.0.0.1", PORT):
            return
        sleep(1)
    raise TimeoutError(f"nothing answers on port {PORT} after {timeout:g} s; see {LOG}")


def stop(process: subprocess.Popen) -> None:
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(30)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
