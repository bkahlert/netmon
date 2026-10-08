import os
import subprocess
from pathlib import Path

import pytest

pytestmark = pytest.mark.tier0
ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "pyproject.toml").exists())


class TestPreviewTargets:
    @pytest.mark.parametrize("target, flavor", [("preview-browser", "browser"), ("preview-vm", "vm"), ("preview", "vm"), ("preview-board", "board")])
    def test_runs_the_preview_in_its_flavor(self, target, flavor):
        result = subprocess.run(["make", "-n", target, "TARGET=pi@netmon.local"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert result.returncode == 0, result.stderr
        assert f"python -m netmon_dev.preview --on {flavor}" in result.stdout

    def test_lists_the_targets_in_help(self):
        result = subprocess.run(["make", "help"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert [name for name in ("preview-browser", "preview-vm", "preview-board") if name in result.stdout] == ["preview-browser", "preview-vm", "preview-board"]


class TestBenchTarget:
    def test_runs_the_bench_script(self):
        result = subprocess.run(["make", "-n", "bench", "TARGET=pi@netmon.local"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert result.returncode == 0, result.stderr
        assert "python -m netmon_dev.bench" in result.stdout

    def test_lists_the_target_in_help(self):
        result = subprocess.run(["make", "help"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert "bench" in result.stdout


class TestToolTargets:
    @pytest.mark.parametrize(
        "target, command",
        [
            ("broker", "python -m netmon_dev.preview.broker"),
            ("vm-device", "python -m netmon_dev.system.vm_device"),
            ("device-model-codes", "python -m netmon_dev.assets.device_model_codes"),
            ("device-icons", "python -m netmon_dev.assets.device_icons"),
        ],
    )
    def test_runs_the_module_entry_point(self, target, command):
        result = subprocess.run(["make", "-n", target], capture_output=True, text=True, check=False, cwd=ROOT)

        assert result.returncode == 0, result.stderr
        assert command in result.stdout


class TestAggregateValidation:
    def test_test_target_includes_layout_validation(self):
        result = subprocess.run(["make", "-n", "test"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert result.returncode == 0, result.stderr
        assert "test-layout" in result.stdout

    def test_test_all_inherits_layout_validation(self):
        result = subprocess.run(["make", "-n", "test-all"], capture_output=True, text=True, check=False, cwd=ROOT)

        assert result.returncode == 0, result.stderr
        assert "test-layout" in result.stdout

    def test_ci_installs_browser_and_runs_layout_validation(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()

        assert "make browser BROWSER_ARGS=--with-deps" in workflow
        assert "make test-layout" in workflow
        assert workflow.index("make build") < workflow.index("make browser BROWSER_ARGS=--with-deps") < workflow.index("make test-layout")

    def test_release_installs_browser_and_runs_layout_validation(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text()

        assert "make browser BROWSER_ARGS=--with-deps" in workflow
        assert "make test-layout" in workflow
        assert workflow.index("make build") < workflow.index("make browser BROWSER_ARGS=--with-deps") < workflow.index("make test-layout") < workflow.index("make test-tier1")


class TestImports:
    @pytest.mark.parametrize(
        "statement",
        [
            "import netmon_dev.preview as preview; assert preview.Netmon.name == 'netmon'",
            "import netmon_dev.preview.broker as broker; assert broker.CONTAINER == 'netmon-preview-broker'",
            "import netmon_dev.preview.scan_fixtures as scan_fixtures; assert scan_fixtures.Scan(1, 2, 3).stable == 2",
            "from netmon_dev.bench import command; assert command.SESSION == 'netmon-bench'",
            "import netmon_dev.assets.device_model_codes as device_model_codes; assert bool(device_model_codes.EXTRA_SYMBOLS)",
            "import netmon_dev.assets.device_icons as device_icons; assert 'Television' in device_icons.KINDS",
            "import netmon_dev.system.vm_device as vm_device; assert vm_device.SOURCE.endswith('netmon.sources')",
            "import tests.browser.layout as layout; assert layout.Page.__name__ == 'Page'",
        ],
    )
    def test_module_is_importable_from_the_repository_root(self, statement):
        env = {**os.environ, "PYTHONPATH": os.pathsep.join([".", "tools"])}
        result = subprocess.run(
            ["uv", "run", "--frozen", "python", "-c", statement],
            cwd=ROOT,
            env=env,
            capture_output=True,
            text=True,
            check=False,
        )

        assert result.returncode == 0, result.stdout + result.stderr
