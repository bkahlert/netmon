"""Over SSH the testkit reads the installed version of `pihero`; these packages compare against their own."""
import subprocess

import pytest


@pytest.fixture(scope="session")
def version(request) -> str:
    if request.config.getoption("--target") == "ssh":
        uri = request.config.getoption("--target-uri")
        user_host, _, port = uri.partition(":")
        cmd = ["ssh", "-o", "BatchMode=yes", *(["-p", port] if port else []), user_host, "dpkg-query -W -f '${Version}' netmon-scanner"]
        result = subprocess.run(cmd, capture_output=True, text=True, check=False)
        if result.returncode != 0 or not result.stdout.strip():
            raise SystemExit(f"cannot read the installed netmon-scanner version from {uri}: {result.stderr.strip() or 'not installed'}")
        return result.stdout.strip()
    from pihero_testkit import build

    return build.version_from_git()
