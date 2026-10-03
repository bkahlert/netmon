"""Helpers for the preview's processes: probing a port, ending on Ctrl-C or SIGTERM, reading a process's command line."""
import signal
import socket
import subprocess


def answers(host: str, port: int, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def command_of(pid: int) -> str | None:
    result = subprocess.run(["ps", "-p", str(pid), "-o", "command="], capture_output=True, text=True, check=False)
    return result.stdout.strip() or None


def raise_on_sigterm() -> None:
    """Makes SIGTERM end the program the way Ctrl-C does, so `finally` blocks and exit stacks run."""

    def interrupt(signum, frame):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, interrupt)


def until_interrupted() -> None:
    try:
        signal.pause()
    except KeyboardInterrupt:
        pass
