import subprocess
from pathlib import Path

import pytest

pytestmark = pytest.mark.tier0


class TestPreviewTargets:
    @pytest.mark.parametrize("target, flavor", [("preview-browser", "browser"), ("preview-vm", "vm"), ("preview", "vm"), ("preview-board", "board")])
    def test_runs_the_preview_in_its_flavor(self, target, flavor):
        result = subprocess.run(["make", "-n", target, "TARGET=pi@netmon.local"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert result.returncode == 0, result.stderr
        assert f"tests/preview.py --on {flavor}" in result.stdout

    def test_lists_the_targets_in_help(self):
        result = subprocess.run(["make", "help"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert [name for name in ("preview-browser", "preview-vm", "preview-board") if name in result.stdout] == ["preview-browser", "preview-vm", "preview-board"]


ROOT = Path(__file__).resolve().parents[1]
