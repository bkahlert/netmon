from pathlib import Path

import pytest

from pihero_testkit import tools

pytestmark = pytest.mark.tier0

ROOT = Path.cwd()
POSTINST = ROOT / "packages" / "netmon-display" / "scripts" / "postinst.sh"
COMMANDS = ("lighty-enable-mod", "deb-systemd-invoke", "deb-systemd-helper")


class TestPostinst:
    def test_retires_the_old_sampler_then_restarts_the_web_server_and_the_kiosk(self):
        stubs = ROOT / "dist" / "stubs" / "postinst"
        stubs.mkdir(parents=True, exist_ok=True)
        for name in COMMANDS:
            stub = stubs / name
            stub.write_text('#!/bin/sh\necho "$0 $*" >&2\n')
            stub.chmod(0o755)
        mounts = [f"{stubs / name}:/usr/local/bin/{name}:ro" for name in COMMANDS] + [f"{stubs}:/run/systemd/system:ro"]

        result = tools.run(["sh", f"/work/{POSTINST.relative_to(ROOT)}"], check=False, capture=True, mounts=mounts)

        assert result.returncode == 0, result.stderr
        calls = [line.split("/")[-1] for line in result.stderr.splitlines() if "deb-systemd" in line]
        assert calls == [
            "deb-systemd-invoke stop netmon-display-stats.service",
            "deb-systemd-helper purge netmon-display-stats.service",
            "deb-systemd-invoke try-restart lighttpd.service",
            "deb-systemd-invoke try-restart pihero-kiosk.service",
        ]
