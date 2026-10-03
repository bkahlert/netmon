"""Tier 2 boots netmon's own device file: the sample rendered for the VM, unless a device directory is given.
The soak and the apt probe register their options and markers here; both are opt-in and skipped on podman."""
import pytest

import booted
import vm_device

BOOTED_ONLY = ("soak", "apt")


def pytest_addoption(parser):
    group = parser.getgroup("netmon")
    group.addoption("--soak-duration", default="10m", help="how long the soak samples, e.g. 10m or 90s")
    group.addoption("--soak-interval", default="30s", help="the time between two samples")
    group.addoption("--kiosk-conf", default=None, help="for --target=vm: a kiosk.conf to write before the soak, for an A/B")
    group.addoption("--apt-timeout", default=300, type=int, help="seconds apt may take next to the stack")
    group.addoption("--apt-package", default="netmon-display", help="the package the apt probe reinstalls")


def pytest_configure(config):
    config.addinivalue_line("markers", "soak: samples both units' memory for minutes on a booted VM or device (vm, ssh), opt-in")
    config.addinivalue_line("markers", "layout: loads the built page in Playwright's WebKit against a scripted broker, opt-in (make test-layout)")
    config.addinivalue_line("markers", "apt: runs apt next to the live stack on a booted VM or device (vm, ssh), opt-in")
    if config.getoption("--target") == "vm" and not config.getoption("--device"):
        config.option.device = str(vm_device.write())


# After the -m deselection, so a run without the display test does not need the browser.
@pytest.hookimpl(trylast=True)
def pytest_collection_modifyitems(config, items):
    if config.getoption("--target") == "podman":
        for item in items:
            if any(marker in item.keywords for marker in BOOTED_ONLY):
                item.add_marker(pytest.mark.skip(reason="needs a booted system"))
        return
    if any(item.path.name in ("test_display.py", "test_layout.py") for item in items) and not booted.webkit_installed():
        raise pytest.UsageError("Playwright's WebKit is not installed; run `make browser`")
