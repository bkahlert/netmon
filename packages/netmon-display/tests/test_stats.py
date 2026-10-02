import json
import os
import subprocess
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

        sample = sample_once(root, tmp_path / "stats.json", lambda: write_counters(root, usec=2_180_000, ticks=500 + int(1.14 * CLK_TCK), pid=42))

        assert sample["interval"] == 1
        assert abs(sample["kioskCpu"] - 118) <= 6
        assert abs(sample["webCpu"] - 114) <= 6
        assert sample["kioskMemory"] == 168_820_736
        assert abs(sample["at"] - time.time()) < 5

    def test_without_the_kiosk_reports_only_the_time(self, tmp_path):
        root = tmp_path / "root"
        (root / "proc").mkdir(parents=True)
        (root / "proc" / "stat").write_text("cpu  1 2 3 4\ncpu0 1 2 3 4\n")

        sample = sample_once(root, tmp_path / "stats.json")

        assert {key: value for key, value in sample.items() if key != "at"} == {"interval": 1, "kioskCpu": None, "webCpu": None, "kioskMemory": None}

    def test_a_replaced_web_process_has_no_cpu_figure_for_that_sample(self, tmp_path):
        root = kiosk_root(tmp_path, usec=0, ticks=500, pid=42, ram=1, swap=0)

        sample = sample_once(root, tmp_path / "stats.json", lambda: write_counters(root, usec=100_000, ticks=10, pid=43))

        assert sample["webCpu"] is None
        assert sample["kioskCpu"] is not None


class TestUsage:
    def test_help_prints_the_header(self):
        result = subprocess.run([str(SCRIPT), "--help"], capture_output=True, text=True, check=False)

        assert result.returncode == 0
        assert result.stdout.startswith("Purpose:")

    def test_an_unknown_option_exits_with_two(self):
        result = subprocess.run([str(SCRIPT), "--bogus"], capture_output=True, text=True, check=False)

        assert result.returncode == 2
        assert "--help" in result.stderr


def sample_once(root: Path, out: Path, advance: Callable[[], None] | None = None) -> dict:
    process = subprocess.Popen([str(SCRIPT), "--root", str(root), "--out", str(out), "--interval", "1", "--once"])
    if advance is not None:
        time.sleep(0.4)
        advance()
    assert process.wait(timeout=10) == 0
    return json.loads(out.read_text())


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


def write_counters(root: Path, usec: int, ticks: int, pid: int) -> None:
    cgroup = root / "sys/fs/cgroup/system.slice/pihero-kiosk.service"
    (cgroup / "cpu.stat").write_text(f"usage_usec {usec}\nuser_usec {usec}\nsystem_usec 0\n")
    (cgroup / "cgroup.procs").write_text(f"1\n{pid}\n")
    proc = root / "proc" / str(pid)
    proc.mkdir(parents=True, exist_ok=True)
    (proc / "comm").write_text("WPEWebProcess\n")
    (proc / "stat").write_text(f"{pid} (WPEWebProcess) S 1 1 1 0 -1 4194560 100 0 0 0 {ticks // 2} {ticks - ticks // 2} 0 0 20 0 8 0 100 0 0 0 0 0 0 0 0\n")
