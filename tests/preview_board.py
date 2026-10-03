"""The preview's real board: the page's address on it, the ssh tunnel to the Mac, and the session files the kiosk reads from /run."""
import subprocess
import sys
import time

from pihero_testkit import ssh

import preview_broker
import preview_kiosk
import preview_process

DEV_REMOTE_PORT = 18081
BROKER_REMOTE_PORT = 18080
RUN_DIR = "/run/netmon-preview"
CONF = f"{RUN_DIR}/kiosk.conf"
DROPIN_DIR = "/run/systemd/system/pihero-kiosk.service.d"
DROPIN = f"{DROPIN_DIR}/preview.conf"
INSTALL = (
    f"sudo install -d {RUN_DIR} {DROPIN_DIR} && sudo tee {CONF} >/dev/null && "
    f"printf '[Service]\\nEnvironmentFile={CONF}\\n' | sudo tee {DROPIN} >/dev/null && "
    "sudo systemctl daemon-reload && sudo systemctl restart pihero-kiosk"
)
RESTORE = (
    f"sudo rm -rf {RUN_DIR} {DROPIN}; sudo rmdir --ignore-fail-on-non-empty {DROPIN_DIR} 2>/dev/null; "
    "sudo systemctl daemon-reload && sudo systemctl restart pihero-kiosk"
)


def host_of(target: str) -> str:
    """Returns the host of user@host[:port]."""
    return target.partition(":")[0].rpartition("@")[2]


def stats_origin(target: str) -> str:
    return f"http://{host_of(target)}"


def on_the_mac(broker: preview_broker.Broker) -> bool:
    """Returns whether the broker runs on the Mac: the fixture, or a HOST:PORT with a loopback host."""
    return broker.kind == preview_broker.FIXTURE or (broker.kind == preview_broker.EXTERNAL and broker.host in preview_kiosk.LOOPBACK)


def page_url(broker: preview_broker.Broker) -> str:
    host, port = ("127.0.0.1", BROKER_REMOTE_PORT) if on_the_mac(broker) else (broker.host, broker.port)
    return f"http://127.0.0.1:{DEV_REMOTE_PORT}/?broker.host={host}&broker.port={port}"


def forwards(broker: preview_broker.Broker, dev_port: int, inspector_port: int) -> list[str]:
    args = ["-R", f"127.0.0.1:{DEV_REMOTE_PORT}:127.0.0.1:{dev_port}", "-L", f"127.0.0.1:{inspector_port}:127.0.0.1:{inspector_port}"]
    if on_the_mac(broker):
        args += ["-R", f"127.0.0.1:{BROKER_REMOTE_PORT}:127.0.0.1:{broker.port}"]
    return args


def tunnel_command(target: str, forward_args: list[str]) -> list[str]:
    user_host, _, port = target.partition(":")
    return [
        "ssh", "-N", "-o", "BatchMode=yes", "-o", "ExitOnForwardFailure=yes", "-o", "ConnectTimeout=10",
        *ssh.KEEPALIVE, *(["-p", port] if port else []), *forward_args, user_host,
    ]


class Board:
    def __init__(self, target: str, run=subprocess.run):
        self.target, self._run = target, run

    def ssh(self, remote: str, input: str | None = None, timeout: float = 60) -> subprocess.CompletedProcess:
        return self._run(ssh.command(self.target, remote), input=input, text=True, capture_output=True, check=False, timeout=timeout)

    def check_kiosk(self) -> None:
        result = self.ssh("dpkg-query -W pihero-kiosk")
        if result.returncode == 255:
            raise RuntimeError(f"cannot reach {self.target} over ssh: {result.stderr.strip()}")
        if result.returncode != 0:
            raise RuntimeError(f"{self.target} has no pihero-kiosk; flash a Pi Hero device file first")

    def install(self, broker: preview_broker.Broker, timeout: float = 90, sleep=time.sleep, clock=time.monotonic) -> None:
        """Points the board's kiosk at the dev server through the tunnel, with the inspector on; raises ValueError for a kiosk.conf it cannot change."""
        current = self.ssh("cat /etc/pihero/kiosk.conf")
        if current.returncode != 0:
            raise RuntimeError(f"cannot read /etc/pihero/kiosk.conf on {self.target}: {current.stderr.strip()}")
        conf = preview_kiosk.session_kiosk_conf(current.stdout, page_url(broker), preview_kiosk.INSPECTOR_PORT)
        since = self.ssh("date '+%Y-%m-%d %H:%M:%S'").stdout.strip()
        result = self.ssh(INSTALL, input=conf)
        if result.returncode != 0:
            raise RuntimeError(f"could not put the session on {self.target}: {result.stderr.strip()}")
        self.wait_loaded(since, timeout, sleep, clock)

    def wait_loaded(self, since: str, timeout: float, sleep, clock) -> None:
        deadline = clock() + timeout
        while clock() < deadline:
            loaded = self.ssh(f"sudo journalctl -u pihero-kiosk --since '{since}' --no-pager | grep -c 'Loaded successfully'").stdout.strip()
            if loaded not in ("", "0"):
                return
            sleep(1)
        raise TimeoutError(f"the kiosk on {self.target} did not load its page within {timeout:g} s; is the dev server up and the tunnel open?")

    def restore(self) -> bool:
        """Removes the session's files and restarts the kiosk; returns whether the board answered, else warns."""
        try:
            result = self.ssh(RESTORE, timeout=30)
        except (subprocess.TimeoutExpired, OSError) as error:
            print(f"could not restore the kiosk on {self.target}: {error}; a reboot of the board removes the session's files", file=sys.stderr)
            return False
        if result.returncode != 0:
            print(f"could not restore the kiosk on {self.target}: {result.stderr.strip()}; a reboot of the board removes the session's files", file=sys.stderr)
        return result.returncode == 0

    def open_tunnel(self, broker: preview_broker.Broker, dev_port: int, inspector_port: int) -> subprocess.Popen:
        tunnel = subprocess.Popen(tunnel_command(self.target, forwards(broker, dev_port, inspector_port)), stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if tunnel.poll() is not None:
                raise RuntimeError(f"the ssh tunnel to {self.target} ended: {tunnel.stderr.read().strip()}")
            if preview_process.answers("127.0.0.1", inspector_port):
                return tunnel
            time.sleep(0.25)
        self.close_tunnel(tunnel)
        raise TimeoutError(f"the ssh tunnel to {self.target} did not come up within 15 s")

    def close_tunnel(self, tunnel: subprocess.Popen) -> None:
        if tunnel.poll() is None:
            tunnel.terminate()
            try:
                tunnel.wait(5)
            except subprocess.TimeoutExpired:
                tunnel.kill()
