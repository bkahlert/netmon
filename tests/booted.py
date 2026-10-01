"""Helpers for tests against a booted target: the scanner's journal, cloud-init's status, the default gateway, and an SSH tunnel."""
import re
import socket
import subprocess
import time

from pihero_testkit.vm import SSH_OPTS, Vm

KNOWN_CLOUD_INIT_WARNING = "cc_netplan_nm_patch"


def journal_until(host, needle: str, attempts: int = 45) -> str:
    log = ""
    for _ in range(attempts):
        log = host.run("journalctl -u netmon-scanner -b --no-pager -o cat").stdout
        if needle in log:
            return log
        time.sleep(2)
    return log


def unexpected_recoverable_errors(status: dict) -> list[str]:
    """Return cloud-init's recoverable errors except Raspberry Pi OS's own warning about a module it names but does not ship."""
    return [message for messages in status.get("recoverable_errors", {}).values() for message in messages if KNOWN_CLOUD_INIT_WARNING not in message]


def gateway_of(routes: str) -> str:
    """Return the gateway of the first default route in the output of `ip -4 route show default`."""
    for line in routes.splitlines():
        words = line.split()
        if len(words) >= 3 and words[0] == "default" and words[1] == "via":
            return words[2]
    raise ValueError(f"no default route in {routes!r}")


def exactly(ip: str) -> re.Pattern:
    return re.compile(rf"^{re.escape(ip)}$")


class Tunnel:
    """Forwards two free local ports to the target's 80 and 8080 with `ssh -N -L` until closed."""

    def __init__(self, target):
        self.http = free_port()
        self.ws = free_port()
        forwards = ["-L", f"127.0.0.1:{self.http}:127.0.0.1:80", "-L", f"127.0.0.1:{self.ws}:127.0.0.1:8080"]
        if isinstance(target, Vm):
            command = ["ssh", "-N", *forwards, "-i", str(target.key), "-p", str(target.port), *SSH_OPTS, f"{target.user}@127.0.0.1"]
        else:
            user_host, _, port = target.uri.partition(":")
            command = ["ssh", "-N", *forwards, *(["-p", port] if port else []), "-o", "BatchMode=yes", user_host]
        self.process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        self._wait_listening()

    def _wait_listening(self, timeout: float = 30) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError(f"the ssh tunnel exited: {self.process.stderr.read()}")
            try:
                with socket.create_connection(("127.0.0.1", self.http), timeout=1):
                    return
            except OSError:
                time.sleep(0.5)
        raise TimeoutError("the ssh tunnel did not come up")

    def close(self) -> None:
        self.process.terminate()
        self.process.wait(10)


def free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]
