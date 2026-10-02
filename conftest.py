"""Tier 2 boots netmon's own device file: the sample rendered for the VM, unless a device directory is given."""
import pytest

import booted
import vm_device


def pytest_configure(config):
    if config.getoption("--target") == "vm" and not config.getoption("--device"):
        config.option.device = str(vm_device.write())


# After the -m deselection, so a run without the display test does not need the browser.
@pytest.hookimpl(trylast=True)
def pytest_collection_modifyitems(config, items):
    if config.getoption("--target") == "podman":
        return
    if any(item.path.name == "test_display.py" for item in items) and not booted.webkit_installed():
        raise pytest.UsageError("Playwright's WebKit is not installed; run `make browser`")
