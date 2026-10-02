import os
import subprocess
import sys
from pathlib import Path

import pytest

import booted

pytestmark = pytest.mark.tier0
ROOT = Path(__file__).resolve().parents[1]


class TestPytestCollectionModifyitems:
    def test_without_webkit_a_display_test_run_stops_before_the_boot(self, tmp_path):
        result = run_pytest("--collect-only", "-q", "-m", "boot", "--target=vm", "tests/test_display.py", browsers=tmp_path)

        assert result.returncode != 0
        assert "make browser" in result.stdout + result.stderr

    def test_with_webkit_the_display_test_collects(self):
        if not booted.webkit_installed():
            pytest.skip("Playwright's WebKit is not installed")

        result = run_pytest("--collect-only", "-q", "-m", "boot", "--target=vm", "tests/test_display.py")

        assert result.returncode == 0, result.stdout + result.stderr

    def test_without_the_display_test_webkit_is_not_needed(self, tmp_path):
        result = run_pytest("--collect-only", "-q", "-m", "installed", "--target=ssh", "--target-uri=pi@example", "packages", browsers=tmp_path)

        assert result.returncode == 0, result.stdout + result.stderr


def run_pytest(*options: str, browsers: Path | None = None) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    if browsers is not None:
        env["PLAYWRIGHT_BROWSERS_PATH"] = str(browsers)
    return subprocess.run(
        [sys.executable, "-m", "pytest", "-p", "no:cacheprovider", *options],
        cwd=ROOT, env=env, check=False, capture_output=True, text=True,
    )
