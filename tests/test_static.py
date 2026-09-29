"""Static checks over the packages, as pihero's own tier 0 runs them: shellcheck, systemd-analyze verify, cloud-init schema."""
import shutil
from importlib.resources import files
from pathlib import Path

import pytest

from pihero_testkit import tools

pytestmark = pytest.mark.tier0

ROOT = Path.cwd()
# The units call binaries the tools container does not have; verify only needs them to exist and be executable.
STUBBED_COMMANDS = ("/usr/bin/java",)
STRIP_RPI_KEYS = (
    "import sys, yaml; d = yaml.safe_load(open(sys.argv[1])); "
    "[d.pop(k, None) for k in ('rpi', 'enable_ssh')]; "
    "open(sys.argv[2], 'w').write('#cloud-config\\n' + yaml.safe_dump(d))"
)


def shell_files():
    for path in (ROOT / "packages").rglob("*"):
        if not path.is_file() or path.is_symlink() or ".build" in path.parts:
            continue
        first_line = path.open("rb").readline()
        if path.suffix == ".sh" or (first_line.startswith(b"#!") and b"sh" in first_line):
            yield path


def unit_files():
    yield from (ROOT / "packages").glob("*/root/usr/lib/systemd/system/*.service")


def device_files():
    yield from (ROOT / "devices").glob("*/user-data")


@pytest.mark.parametrize("script", sorted(shell_files()), ids=lambda p: str(p.relative_to(ROOT)))
def test_shell_file_passes_shellcheck(script):
    result = tools.run(["shellcheck", f"/work/{script.relative_to(ROOT)}"], check=False, capture=True)

    assert result.returncode == 0, result.stdout


@pytest.mark.parametrize("unit", sorted(unit_files()), ids=lambda p: p.name)
def test_unit_passes_systemd_analyze_verify(unit):
    stubs = ROOT / "dist" / "stubs"
    stubs.mkdir(parents=True, exist_ok=True)
    mounts = []
    for command in STUBBED_COMMANDS:
        stub = stubs / Path(command).name
        stub.write_text("#!/bin/sh\n")
        stub.chmod(0o755)
        mounts.append(f"{stub}:{command}:ro")

    result = tools.run(["systemd-analyze", "verify", "--man=no", f"/work/{unit.relative_to(ROOT)}"], mounts=mounts, check=False, capture=True)

    assert result.returncode == 0, result.stdout + result.stderr


@pytest.mark.parametrize("user_data", sorted(device_files()), ids=lambda p: p.parent.name)
def test_device_file_validates_against_cloud_init_schema(user_data):
    staged = ROOT / "dist" / "schema" / user_data.parent.name
    staged.mkdir(parents=True, exist_ok=True)
    shutil.copy(user_data, staged / "user-data")
    relative = staged.relative_to(ROOT)

    result = tools.run(
        ["sh", "-c", f"python3 -c \"{STRIP_RPI_KEYS}\" /work/{relative}/user-data /work/{relative}/stripped && cloud-init schema --config-file /work/{relative}/stripped"],
        check=False, capture=True,
    )

    assert user_data.read_text().startswith("#cloud-config\n")
    assert result.returncode == 0, result.stdout + result.stderr
    assert "Valid schema" in result.stdout
