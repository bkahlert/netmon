"""The preview's real board: the page's address on it, the ssh tunnel to the Mac, and the session files the kiosk reads from /run."""
import re
import subprocess
import sys
import time
from pathlib import Path

from pihero_testkit import ssh

import preview_broker
import preview_kiosk
import preview_process

CHANNEL_NOISE = re.compile(r"channel \d+: open failed")
TUNNEL_LOG = Path(__file__).resolve().parents[1] / "dist" / "preview" / "tunnel.log"
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
END_STALE_FORWARDS = (
    f"sudo ss -ltnpH | grep -E '127\\.0\\.0\\.1:({DEV_REMOTE_PORT}|{BROKER_REMOTE_PORT}) ' | grep sshd | "
    "grep -o 'pid=[0-9]*' | cut -d= -f2 | sort -u | xargs -r sudo kill"
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
        "ssh", "-N", "-4", "-o", "BatchMode=yes", "-o", "ExitOnForwardFailure=yes", "-o", "ConnectTimeout=10",
        *ssh.KEEPALIVE, *(["-p", port] if port else []), *forward_args, user_host,
    ]


class Board:
    def __init__(self, target: str, run=subprocess.run, tunnel_log: Path = TUNNEL_LOG):
        self.target, self._run, self.tunnel_log = target, run, tunnel_log

    def ssh(self, remote: str, input: str | None = None, timeout: float = 60) -> subprocess.CompletedProcess:
        try:
            return self._run(ssh.command(self.target, remote), input=input, text=True, capture_output=True, check=False, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise TimeoutError(f"{self.target} did not answer within {timeout:g} s") from None

    def check_kiosk(self) -> None:
        result = self.ssh("dpkg-query -W pihero-kiosk")
        if result.returncode == 255:
            raise RuntimeError(f"cannot reach {self.target} over ssh: {result.stderr.strip()}")
        if result.returncode != 0:
            raise RuntimeError(f"{self.target} has no pihero-kiosk; flash a Pi Hero device file first")

    def session_conf(self, broker: preview_broker.Broker) -> str:
        """Returns the board's kiosk.conf pointed at the dev server through the tunnel, with the inspector on, changing nothing on the board; raises ValueError for a kiosk.conf it cannot change."""
        current = self.ssh("cat /etc/pihero/kiosk.conf")
        if current.returncode != 0:
            raise RuntimeError(f"cannot read /etc/pihero/kiosk.conf on {self.target}: {current.stderr.strip()}")
        return preview_kiosk.session_kiosk_conf(current.stdout, page_url(broker), preview_kiosk.INSPECTOR_PORT)

    def install(self, conf: str, tunnel: subprocess.Popen, timeout: float = 90, sleep=time.sleep, clock=time.monotonic) -> None:
        """Puts the session on the board and waits for the kiosk to load its page, failing early with the tunnel's own message if the tunnel ends."""
        since = self.ssh("date '+%Y-%m-%d %H:%M:%S'").stdout.strip()
        result = self.ssh(INSTALL, input=conf)
        if result.returncode != 0:
            raise RuntimeError(f"could not put the session on {self.target}: {result.stderr.strip()}")
        self.wait_loaded(since, tunnel, timeout, sleep, clock)

    def wait_loaded(self, since: str, tunnel: subprocess.Popen, timeout: float, sleep, clock) -> None:
        deadline = clock() + timeout
        while clock() < deadline:
            if tunnel.poll() is not None:
                raise RuntimeError(self.tunnel_ended(tunnel))
            loaded = self.ssh(f"sudo journalctl -u pihero-kiosk --since '{since}' --no-pager | grep -c 'Loaded successfully'").stdout.strip()
            if loaded not in ("", "0"):
                return
            sleep(1)
        raise TimeoutError(f"the kiosk on {self.target} did not load its page within {timeout:g} s; is the dev server up and the tunnel open?")

    def restore(self) -> bool:
        """Removes the session's files and restarts the kiosk; returns whether the board answered, else warns."""
        try:
            result = self.ssh(RESTORE, timeout=30)
        except OSError as error:
            print(f"could not restore the kiosk on {self.target}: {error}; a reboot of the board removes the session's files", file=sys.stderr)
            return False
        if result.returncode != 0:
            print(f"could not restore the kiosk on {self.target}: {result.stderr.strip()}; a reboot of the board removes the session's files", file=sys.stderr)
        return result.returncode == 0

    def open_tunnel(self, broker: preview_broker.Broker, dev_port: int, inspector_port: int) -> subprocess.Popen:
        """Opens the tunnel after ending the board's side of a dead one, which keeps the reverse ports until sshd notices; raises RuntimeError when it ends early."""
        self.ssh(END_STALE_FORWARDS, timeout=30)
        self.tunnel_log.parent.mkdir(parents=True, exist_ok=True)
        with self.tunnel_log.open("w") as log:
            tunnel = subprocess.Popen(tunnel_command(self.target, forwards(broker, dev_port, inspector_port)), stdout=subprocess.DEVNULL, stderr=log, text=True)
        try:
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                if tunnel.poll() is not None:
                    raise RuntimeError(self.tunnel_ended(tunnel))
                if preview_process.answers("127.0.0.1", inspector_port):
                    return tunnel
                time.sleep(0.25)
            raise TimeoutError(f"the ssh tunnel to {self.target} did not come up within 15 s; see {self.tunnel_log}")
        except BaseException:
            self.close_tunnel(tunnel)
            raise

    def tunnel_problem(self, tunnel: subprocess.Popen) -> str | None:
        """Returns why the tunnel ended, or None while it runs."""
        return self.tunnel_ended(tunnel) if tunnel.poll() is not None else None

    def tunnel_ended(self, tunnel: subprocess.Popen) -> str:
        lines = self.tunnel_log.read_text(errors="replace").strip().splitlines() if self.tunnel_log.exists() else []
        reasons = [line for line in lines if not CHANNEL_NOISE.match(line)]
        if reasons:
            return f"the ssh tunnel to {self.target} ended: {reasons[-1]}"
        return f"the ssh tunnel to {self.target} ended with status {tunnel.poll()} and no message; see {self.tunnel_log}"

    def close_tunnel(self, tunnel: subprocess.Popen) -> None:
        if tunnel.poll() is None:
            tunnel.terminate()
            try:
                tunnel.wait(5)
            except subprocess.TimeoutExpired:
                tunnel.kill()
