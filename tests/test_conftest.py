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

    def test_the_soak_collects_with_its_marker_and_options(self):
        result = run_pytest("--collect-only", "-q", "-m", "soak", "--target=ssh", "--target-uri=pi@example", "--soak-duration=1m", "--soak-interval=5s", "tests")

        assert result.returncode == 0, result.stdout + result.stderr
        assert "test_soak.py" in result.stdout

    def test_installed_or_boot_leaves_the_soak_out(self):
        result = run_pytest("--collect-only", "-q", "-m", "installed or boot", "--target=ssh", "--target-uri=pi@example", "tests")

        assert result.returncode == 0, result.stdout + result.stderr
        assert "test_soak.py" not in result.stdout

    def test_on_podman_the_soak_is_skipped_for_needing_a_booted_system(self):
        result = run_pytest("-m", "soak", "--target=podman", "-rs", "tests")

        assert result.returncode == 0, result.stdout + result.stderr
        assert "1 skipped" in result.stdout
        assert "needs a booted system" in result.stdout

    def test_the_apt_probe_collects_only_with_its_marker(self):
        selected = run_pytest("--collect-only", "-q", "-m", "apt", "--target=ssh", "--target-uri=pi@example", "--apt-timeout=60", "tests")
        default = run_pytest("--collect-only", "-q", "-m", "installed or boot", "--target=ssh", "--target-uri=pi@example", "tests")

        assert selected.returncode == 0 and "test_apt.py" in selected.stdout, selected.stdout + selected.stderr
        assert "test_apt.py" not in default.stdout


def run_pytest(*options: str, browsers: Path | None = None) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    if browsers is not None:
        env["PLAYWRIGHT_BROWSERS_PATH"] = str(browsers)
    return subprocess.run(
        [sys.executable, "-m", "pytest", "-p", "no:cacheprovider", *options],
        cwd=ROOT, env=env, check=False, capture_output=True, text=True,
    )
