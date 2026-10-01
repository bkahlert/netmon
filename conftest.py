"""Tier 2 boots netmon's own device file: the sample rendered for the VM, unless a device directory is given."""
import vm_device


def pytest_configure(config):
    if config.getoption("--target") == "vm" and not config.getoption("--device"):
        config.option.device = str(vm_device.write())
