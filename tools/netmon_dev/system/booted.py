"""Helpers for tests against a booted target: the scanner's journal, cloud-init's status, the default gateway, and an SSH tunnel."""
import re
import socket
import subprocess
import time
from pathlib import Path

from pihero_testkit.ssh import SshTarget
from pihero_testkit.vm import SSH_OPTS
from playwright.sync_api import sync_playwright

KNOWN_CLOUD_INIT_WARNING = "cc_netplan_nm_patch"


def webkit_installed() -> bool:
    with sync_playwright() as playwright:
        return Path(playwright.webkit.executable_path).exists()


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


def kiosk_conf(text: str) -> dict[str, str]:
    """Return the assignments of an EnvironmentFile such as /etc/pihero/kiosk.conf, double quotes stripped, comments and blanks skipped."""
    result = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        name, _, value = stripped.partition("=")
        result[name.strip()] = value.strip().strip('"')
    return result


def exactly(ip: str) -> re.Pattern:
    return re.compile(rf"^{re.escape(ip)}$")


def tunnel_command(target, http: int, ws: int) -> list[str]:
    """Return the `ssh -N -L` command forwarding the local ports `http` and `ws` to the target's 80 and 8080."""
    forwards = ["-L", f"127.0.0.1:{http}:127.0.0.1:80", "-L", f"127.0.0.1:{ws}:127.0.0.1:8080"]
    # A multiplexed client hands the forwards to the master and exits at once; the tunnel has to be its own connection.
    unshared = ["-o", "ControlMaster=no", "-o", "ControlPath=none"]
    if isinstance(target, SshTarget):
        user_host, _, port = target.uri.partition(":")
        return ["ssh", "-N", *forwards, *(["-p", port] if port else []), *unshared, "-o", "BatchMode=yes", user_host]
    return ["ssh", "-N", *forwards, "-i", str(target.key), "-p", str(target.port), *unshared, *SSH_OPTS, f"{target.user}@127.0.0.1"]


class Tunnel:
    """Forwards two free local ports to the target's 80 and 8080 with `ssh -N -L` until closed."""

    def __init__(self, target):
        self.http = free_port()
        self.ws = free_port()
        self.process = subprocess.Popen(tunnel_command(target, self.http, self.ws), stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        self._wait_listening()

    def _wait_listening(self, timeout: float = 30) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError(f"the ssh tunnel exited with {self.process.returncode}: {self.process.stderr.read()}")
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
