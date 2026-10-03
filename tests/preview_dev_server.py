"""Gradle's dev server for the display, on the port the preview's pages and the kiosk use; the device flavor adds a proxy for the board's stats."""
import os
import signal
import subprocess
import time
from pathlib import Path

import preview_process

ROOT = Path(__file__).resolve().parents[1]
PORT = 8081
LOG = ROOT / "dist" / "preview" / "gradle.log"
STATS_PROXY_ENV = "NETMON_STATS_PROXY"


def ensure(stats_proxy: str | None = None) -> subprocess.Popen:
    """Starts the dev server and returns its process; raises RuntimeError when something already answers on its port."""
    if preview_process.answers("127.0.0.1", PORT):
        raise RuntimeError(f"something already answers on port {PORT}; end it first")
    process = start(stats_proxy)
    try:
        wait_until_serving(process)
    except BaseException:
        stop(process)
        raise
    return process


def start(stats_proxy: str | None = None) -> subprocess.Popen:
    LOG.parent.mkdir(parents=True, exist_ok=True)
    env = {**os.environ, **({STATS_PROXY_ENV: stats_proxy} if stats_proxy else {})}
    return subprocess.Popen(
        ["./gradlew", "--console=plain", "jsBrowserDevelopmentRun", "--continuous"],
        cwd=ROOT, env=env, stdout=LOG.open("w"), stderr=subprocess.STDOUT, start_new_session=True,
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
