from pathlib import Path

import pytest

import preview_device
import vm_device

pytestmark = pytest.mark.tier0
SAMPLE = vm_device.SAMPLE.read_text()
KEY = "ssh-ed25519 AAAATEST pihero-testkit"


class TestRender:
    def test_leaves_out_netmons_packages(self):
        text = preview_device.render(SAMPLE, KEY)

        assert "  - pihero\n" in text
        assert "netmon-scanner" not in text
        assert "netmon-display" not in text

    def test_installs_the_kiosk_from_pi_heros_own_source(self):
        text = preview_device.render(SAMPLE, KEY)

        assert "  - pihero-kiosk\n" in text

    def test_leaves_out_netmons_apt_source(self):
        text = preview_device.render(SAMPLE, KEY)

        assert "netmon.sources" not in text
        assert "bkahlert.github.io/netmon" not in text
        assert "10.0.2.2:8000" not in text

    def test_leaves_out_the_boot_config_lines_of_netmons_packages(self):
        text = preview_device.render(SAMPLE, KEY)

        assert "bootconfig add cmdline" not in text

    def test_keeps_the_kiosk_settings_and_the_testkit_user(self):
        text = preview_device.render(SAMPLE, KEY)

        assert "COG_PLATFORM_DRM_VIDEO_MODE=800x480" in text
        assert "  - name: pihero\n" in text
        assert f"      - {KEY}\n" in text

    def test_changes_nothing_but_what_it_leaves_out(self):
        rendered = vm_device.render(SAMPLE, KEY).splitlines()

        kept = preview_device.render(SAMPLE, KEY).splitlines()

        assert [line for line in kept if line not in rendered] == ["  - pihero-kiosk"]
        assert len(rendered) - len(kept) == 9


class TestLayerName:
    def test_is_twelve_hex_digits(self):
        name = preview_device.layer_name("aa0c21373d89", "user-data")

        assert len(name) == 12 and int(name, 16) >= 0

    def test_is_stable(self):
        assert preview_device.layer_name("a", "b") == preview_device.layer_name("a", "b")

    @pytest.mark.parametrize("other", [("x", "b"), ("a", "x")])
    def test_changes_with_the_base_image_and_with_the_user_data(self, other):
        assert preview_device.layer_name(*other) != preview_device.layer_name("a", "b")

    def test_does_not_confuse_where_the_two_parts_meet(self):
        assert preview_device.layer_name("ab", "c") != preview_device.layer_name("a", "bc")


class TestLayerFor:
    def test_lives_under_the_cache_in_a_directory_of_its_name(self, tmp_path):
        base = type("Base", (), {"rootfs": Path("/cache/base/aa0c21373d89/rootfs.qcow2")})()

        layer = preview_device.layer_for(base, "user-data", cache=tmp_path)

        name = preview_device.layer_name("aa0c21373d89", "user-data")
        assert layer == preview_device.Layer(tmp_path / name / "rootfs.qcow2", tmp_path / name / "bootfs.img")
