"""One preview session: a VM on a throwaway overlay of the preview's layer, with its window, kiosk settings and inspector tunnel."""
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Callable

from pihero_testkit import prepare
from pihero_testkit.vm import SSH_OPTS, Vm

import preview_device
import preview_kiosk

WINDOW_POINTS = (800, 512)
WINDOW_ORIGIN = (120, 80)
PROCESS = "qemu-system-aarch64"


class Session:
    def __init__(self, layer: preview_device.Layer, directory: Path, accel: str = "hvf", window: bool = True):
        self.layer, self.directory, self.accel, self.window = layer, directory, accel, window
        self.vm: Vm | None = None
        self.tunnel: subprocess.Popen | None = None

    def start(self, on_qemu: Callable[[int], None] | None = None) -> Vm:
        self.directory.mkdir(parents=True, exist_ok=True)
        bootfs = self.directory / "bootfs.img"
        shutil.copy(self.layer.bootfs, bootfs)
        bootfs.chmod(0o644)
        self.vm = Vm(prepare.prepare(), bootfs, self.directory, accel=self.accel, window=self.window, backing=self.layer.rootfs, repo_port=0).start()
        if on_qemu:
            on_qemu(self.vm.process.pid)
        self.vm.wait_ssh()
        return self.vm

    def place_window(self, attempts: int = 20) -> bool:
        """Moves the window onto the main display at 800 by 480 of picture; returns whether macOS allowed it."""
        return self.resize_window(*WINDOW_POINTS, origin=WINDOW_ORIGIN, attempts=attempts)

    def resize_window(self, width: int, height: int, origin: tuple[int, int] | None = None, attempts: int = 5) -> bool:
        target = f'tell application "System Events" to tell process "{PROCESS}"'
        script = [f"{target} to set size of window 1 to {{{width}, {height}}}"]
        if origin:
            script.insert(0, f"{target} to set position of window 1 to {{{origin[0]}, {origin[1]}}}")
        for _ in range(attempts):
            result = subprocess.run(["osascript", *[arg for line in script for arg in ("-e", line)]], capture_output=True, text=True, check=False)
            if result.returncode == 0:
                return True
            time.sleep(1)
        print("could not place the VM's window; allow your terminal under Privacy & Security > Accessibility", file=sys.stderr)
        return False

    def configure_kiosk(self, url: str, inspector_port: int) -> None:
        current = self.vm.ssh("cat /etc/pihero/kiosk.conf").stdout
        updated = preview_kiosk.session_kiosk_conf(current, url, inspector_port)
        subprocess.run(self._ssh("sudo tee /etc/pihero/kiosk.conf >/dev/null"), input=updated, text=True, check=True, capture_output=True)
        self.restart_kiosk()

    def restart_kiosk(self, timeout: float = 90) -> None:
        since = self.vm.ssh("date '+%Y-%m-%d %H:%M:%S'").stdout.strip()
        self.vm.ssh("sudo systemctl restart pihero-kiosk")
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            loaded = self.vm.ssh(f"sudo journalctl -u pihero-kiosk --since '{since}' --no-pager | grep -c 'Loaded successfully'")
            if loaded.stdout.strip() not in ("", "0"):
                return
            time.sleep(1)
        raise TimeoutError(f"the kiosk did not load its page within {timeout:g} s; see {self.vm.serial_log}")

    def open_tunnel(self, local_port: int, remote_port: int) -> None:
        self.tunnel = subprocess.Popen(self._ssh(None, "-N", "-L", f"127.0.0.1:{local_port}:127.0.0.1:{remote_port}"), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def stop(self) -> None:
        if self.tunnel and self.tunnel.poll() is None:
            self.tunnel.terminate()
        vm = self.vm
        if vm and vm.process and vm.process.poll() is None:
            try:
                vm.ssh("sudo poweroff", timeout=15)
                vm.wait_exit(timeout=30)
            except subprocess.TimeoutExpired:
                pass
            vm.stop()
        shutil.rmtree(self.directory, ignore_errors=True)

    def _ssh(self, command: str | None, *options: str) -> list[str]:
        argv = ["ssh", "-i", str(self.vm.key), "-p", str(self.vm.port), *SSH_OPTS, *options, f"{self.vm.user}@127.0.0.1"]
        return argv + [command] if command else argv
