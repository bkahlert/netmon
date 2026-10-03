import json
import os
import subprocess
import sys
import time
from collections.abc import Callable
from pathlib import Path

import pytest

pytestmark = pytest.mark.tier0
SCRIPT = Path(__file__).resolve().parents[1] / "root" / "usr" / "lib" / "netmon" / "netmon-display-stats"
CLK_TCK = os.sysconf("SC_CLK_TCK")


class TestSample:
    def test_reports_the_kiosks_cpu_the_web_processs_cpu_and_the_memory(self, tmp_path):
        root = kiosk_root(tmp_path, usec=1_000_000, ticks=500, pid=42, ram=160_000_000, swap=8_820_736)

        sample = sample_once(
            root, tmp_path / "stats.json", lambda: write_counters(root, usec=2_180_000, ticks=500 + 114 * CLK_TCK // 100, pid=42, uptime="1001.00")
        )

        assert sample["interval"] == 1
        assert sample["kioskCpu"] == 118
        assert sample["webCpu"] == 114
        assert sample["kioskMemory"] == 168_820_736
        assert abs(sample["at"] - time.time()) < 5

    def test_without_the_kiosk_reports_only_the_time(self, tmp_path):
        root = tmp_path / "root"
        (root / "proc").mkdir(parents=True)
        (root / "proc" / "stat").write_text("cpu  1 2 3 4\ncpu0 1 2 3 4\n")
        (root / "proc" / "uptime").write_text("1000.00 4000.00\n")

        sample = sample_once(root, tmp_path / "stats.json")

        assert {key: value for key, value in sample.items() if key != "at"} == {"interval": 1, "kioskCpu": None, "webCpu": None, "kioskMemory": None}

    def test_a_replaced_web_process_has_no_cpu_figure_for_that_sample(self, tmp_path):
        root = kiosk_root(tmp_path, usec=0, ticks=500, pid=42, ram=1, swap=0)

        sample = sample_once(root, tmp_path / "stats.json", lambda: write_counters(root, usec=100_000, ticks=10, pid=43, uptime="1001.00"))

        assert sample["webCpu"] is None
        assert sample["kioskCpu"] is not None

    def test_a_restarted_kiosk_has_no_cpu_figure_for_that_sample(self, tmp_path):
        root = kiosk_root(tmp_path, usec=5_000_000, ticks=500, pid=42, ram=1, swap=0)

        sample = sample_once(root, tmp_path / "stats.json", lambda: write_counters(root, usec=20_000, ticks=400, pid=42, uptime="1001.00"))

        assert sample["kioskCpu"] is None
        assert sample["webCpu"] is None
        assert sample["kioskMemory"] == 1

    def test_samples_the_same_under_a_decimal_comma_locale(self, tmp_path):
        root = kiosk_root(tmp_path, usec=1_000_000, ticks=500, pid=42, ram=1, swap=0)

        sample = sample_once(
            root,
            tmp_path / "stats.json",
            lambda: write_counters(root, usec=2_180_000, ticks=500 + 114 * CLK_TCK // 100, pid=42, uptime="1001.00"),
            env={"LC_ALL": "de_DE.UTF-8"},
        )

        assert sample["kioskCpu"] == 118
        assert sample["webCpu"] == 114

    @pytest.mark.parametrize(
        "file, figure",
        [
            ("sys/fs/cgroup/system.slice/pihero-kiosk.service/cgroup.procs", "webCpu"),
            ("sys/fs/cgroup/system.slice/pihero-kiosk.service/cpu.stat", "kioskCpu"),
            ("sys/fs/cgroup/system.slice/pihero-kiosk.service/memory.current", "kioskMemory"),
            ("proc/42/comm", "webCpu"),
            ("proc/42/stat", "webCpu"),
        ],
    )
    def test_a_kiosk_file_that_fails_to_open_is_a_missing_figure(self, tmp_path, file, figure):
        root = kiosk_root(tmp_path, usec=1_000_000, ticks=500, pid=42, ram=1, swap=0)
        file_that_cannot_be_opened(root / file)

        sample = sample_once(root, tmp_path / "stats.json", lambda: (root / "proc" / "uptime").write_text("1001.00 4000.00\n"))

        assert sample[figure] is None
        assert set(sample) == {"at", "interval", "kioskCpu", "webCpu", "kioskMemory"}


class TestUsage:
    def test_help_prints_the_header(self):
        result = subprocess.run([str(SCRIPT), "--help"], capture_output=True, text=True, check=False)

        assert result.returncode == 0
        assert result.stdout.startswith("Purpose:")

    def test_an_unknown_option_exits_with_two(self):
        result = subprocess.run([str(SCRIPT), "--bogus"], capture_output=True, text=True, check=False)

        assert result.returncode == 2
        assert "--help" in result.stderr


def sample_once(root: Path, out: Path, advance: Callable[[], None] | None = None, env: dict[str, str] | None = None) -> dict:
    process = subprocess.Popen([str(SCRIPT), "--root", str(root), "--out", str(out), "--interval", "1", "--once"], env={**os.environ, **(env or {})})
    if advance is not None:
        time.sleep(0.4)
        advance()
    assert process.wait(timeout=10) == 0
    return json.loads(out.read_text())


def file_that_cannot_be_opened(path: Path) -> None:
    # A socket passes the readable test and fails to open, the state of a file gone between the test and the open.
    path.unlink()
    subprocess.run([sys.executable, "-c", f"import socket; socket.socket(socket.AF_UNIX).bind({path.name!r})"], cwd=path.parent, check=True)


def kiosk_root(tmp_path: Path, usec: int, ticks: int, pid: int, ram: int, swap: int) -> Path:
    root = tmp_path / "root"
    cgroup = root / "sys/fs/cgroup/system.slice/pihero-kiosk.service"
    cgroup.mkdir(parents=True)
    (root / "proc").mkdir(parents=True, exist_ok=True)
    (root / "proc" / "stat").write_text("cpu  1 2 3 4\ncpu0 1 2 3 4\n")
    (cgroup / "memory.current").write_text(f"{ram}\n")
    (cgroup / "memory.swap.current").write_text(f"{swap}\n")
    write_counters(root, usec=usec, ticks=ticks, pid=pid)
    return root


def write_counters(root: Path, usec: int, ticks: int, pid: int, uptime: str = "1000.00") -> None:
    cgroup = root / "sys/fs/cgroup/system.slice/pihero-kiosk.service"
    (root / "proc" / "uptime").write_text(f"{uptime} 4000.00\n")
    (cgroup / "cpu.stat").write_text(f"usage_usec {usec}\nuser_usec {usec}\nsystem_usec 0\n")
    (cgroup / "cgroup.procs").write_text(f"1\n{pid}\n")
    proc = root / "proc" / str(pid)
    proc.mkdir(parents=True, exist_ok=True)
    (proc / "comm").write_text("WPEWebProcess\n")
    (proc / "stat").write_text(f"{pid} (WPEWebProcess) S 1 1 1 0 -1 4194560 100 0 0 0 {ticks // 2} {ticks - ticks // 2} 0 0 20 0 8 0 100 0 0 0 0 0 0 0 0\n")
