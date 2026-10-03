"""Helpers for the preview's processes: probing a port, ending on Ctrl-C or SIGTERM, reading a process's command line."""
import signal
import socket
import subprocess
import time
from typing import Callable


def answers(host: str, port: int, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def command_of(pid: int) -> str | None:
    """Returns the command line of a running process; None if it is gone or has ended and only waits to be reaped."""
    result = subprocess.run(["ps", "-p", str(pid), "-o", "stat=,command="], capture_output=True, text=True, check=False)
    state, _, command = result.stdout.strip().partition(" ")
    return None if not command or state.startswith("Z") else command.strip()


def raise_on_sigterm() -> None:
    """Makes SIGTERM end the program the way Ctrl-C does, so `finally` blocks and exit stacks run."""

    def interrupt(signum, frame):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, interrupt)


def until_interrupted(watch: Callable[[], str | None] | None = None, interval: float = 1.0, sleep=time.sleep) -> None:
    """Waits for Ctrl-C; with a `watch`, asks it every `interval` seconds and raises RuntimeError with the problem it reports."""
    try:
        if watch is None:
            signal.pause()
        while True:
            problem = watch()
            if problem:
                raise RuntimeError(problem)
            sleep(interval)
    except KeyboardInterrupt:
        pass
