"""The VM the preview boots: the sample device file without netmon's packages, provisioned once and kept as a read-only layer."""
import hashlib
import os
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

from pihero_testkit import prepare
from pihero_testkit.vm import provisioned_vm

import vm_device

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "dist" / "preview" / "preview-device"
CACHE = Path.home() / ".cache" / "pihero" / "preview"
NETMON_SOURCE = "  - path: /etc/apt/sources.list.d/netmon.sources"
NETMON_PACKAGES = ("netmon-scanner", "netmon-display")
KIOSK_PACKAGE = "pihero-kiosk"


@dataclass(frozen=True)
class Layer:
    rootfs: Path
    bootfs: Path


def render(sample: str, key: str) -> str:
    """Returns the sample device file for the VM minus netmon's apt source, packages and boot config lines: the page and the broker come from the Mac.

    The kiosk stays: the sample only gets it through netmon-display, so it is named here.
    """
    text = vm_device.render(sample, key)
    text = text.replace(vm_device.block(text, NETMON_SOURCE), "")
    for package in NETMON_PACKAGES:
        text = text.replace(f"  - {package}\n", f"  - {KIOSK_PACKAGE}\n" if package == "netmon-display" else "")
    return "".join(line for line in text.splitlines(keepends=True) if "--package netmon-" not in line)


def write(out: Path = OUT, sample: Path = vm_device.SAMPLE) -> Path:
    out.mkdir(parents=True, exist_ok=True)
    (out / "user-data").write_text(render(sample.read_text(), vm_device.PUBLIC_KEY.read_text().strip()))
    return out


def layer_name(base_id: str, user_data: str) -> str:
    return hashlib.sha256(f"{base_id}\0{user_data}".encode()).hexdigest()[:12]


def layer_for(base, user_data: str, cache: Path = CACHE) -> Layer:
    directory = cache / layer_name(base.rootfs.parent.name, user_data)
    return Layer(directory / "rootfs.qcow2", directory / "bootfs.img")


def ensure_layer(accel: str = "hvf", cache: Path = CACHE) -> Layer:
    """Returns the layer for the current base image and device file, building it (about 2.5 minutes) if the cache has none."""
    base = prepare.prepare()
    device = write()
    layer = layer_for(base, (device / "user-data").read_text(), cache)
    if layer.rootfs.exists() and layer.bootfs.exists():
        return layer
    print("building the preview's base layer, once per base image and device file (about 2.5 minutes)", file=sys.stderr, flush=True)
    building = layer.rootfs.parent.with_name(layer.rootfs.parent.name + ".building")
    shutil.rmtree(building, ignore_errors=True)
    building.mkdir(parents=True)
    with provisioned_vm([], device, accel, keep=False) as vm:
        vm.ssh("sudo poweroff", timeout=30)
        vm.wait_exit()
        shutil.copy(vm.overlay, building / "rootfs.qcow2")
        shutil.copy(vm.bootfs, building / "bootfs.img")
    for artifact in building.iterdir():
        artifact.chmod(0o444)
    os.replace(building, layer.rootfs.parent)
    return layer
