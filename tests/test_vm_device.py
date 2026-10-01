import subprocess
import sys
from pathlib import Path

import pytest

import vm_device

pytestmark = pytest.mark.tier0
ROOT = Path(__file__).resolve().parents[1]
SAMPLE = (ROOT / "devices" / "sample" / "user-data").read_text()
KEY = "ssh-ed25519 AAAATEST pihero-testkit"


class TestRender:
    def test_renames_the_user_and_sets_the_testkit_key(self):
        result = vm_device.render(SAMPLE, key=KEY)

        assert "  - name: pihero\n" in result
        assert "  - name: pi\n" not in result
        assert f"    ssh_authorized_keys:\n      - {KEY}\n" in result

    def test_points_the_netmon_source_at_the_local_repository(self):
        result = vm_device.render(SAMPLE, key=KEY)

        assert "      URIs: http://10.0.2.2:8000/\n      Suites: ./\n      Trusted: yes\n" in result
        assert "bkahlert.github.io/netmon" not in result
        assert "bkahlert.github.io/pihero" in result

    def test_changes_nothing_else(self):
        result = vm_device.render(SAMPLE, key=KEY)

        assert without_edited_blocks(result) == without_edited_blocks(SAMPLE)

    def test_on_a_file_without_a_users_block_raises(self):
        with pytest.raises(ValueError, match="users:"):
            vm_device.render("#cloud-config\nhostname: x\n", key=KEY)

    def test_on_two_users_raises(self):
        two = SAMPLE.replace("rpi:\n", "  - name: second\n    ssh_authorized_keys:\n      - ssh-ed25519 BBBB second\nrpi:\n")

        with pytest.raises(ValueError, match="one user"):
            vm_device.render(two, key=KEY)


class TestWrite:
    def test_writes_only_user_data_with_the_testkit_key(self, tmp_path):
        out = vm_device.write(tmp_path / "vm-device")

        assert [p.name for p in out.iterdir()] == ["user-data"]
        text = (out / "user-data").read_text()
        assert text.startswith("#cloud-config\n")
        assert vm_device.PUBLIC_KEY.read_text().strip() in text


class TestPytestConfigure:
    def test_on_the_vm_target_without_a_device_generates_it(self):
        (vm_device.OUT / "user-data").unlink(missing_ok=True)

        collect_only("--target=vm")

        assert (vm_device.OUT / "user-data").exists()

    def test_on_an_explicit_device_leaves_it_alone(self):
        (vm_device.OUT / "user-data").unlink(missing_ok=True)

        collect_only("--target=vm", "--device=devices/sample")

        assert not (vm_device.OUT / "user-data").exists()


def without_edited_blocks(text: str) -> str:
    for start in ("users:", "  - path: /etc/apt/sources.list.d/netmon.sources"):
        text = text.replace(vm_device.block(text, start), "")
    return text


def collect_only(*options: str) -> None:
    subprocess.run(
        [sys.executable, "-m", "pytest", "--collect-only", "-q", "-p", "no:cacheprovider", *options, "tests/test_vm_device.py"],
        cwd=ROOT, check=True, capture_output=True, text=True,
    )
