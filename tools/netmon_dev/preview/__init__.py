"""make preview-browser, preview-vm and preview-board: netmon's page from the dev server in a browser, a VM's kiosk or a board's kiosk."""
import sys
from pathlib import Path

from pihero_testkit import device_file
from pihero_testkit.preview import DevServer, Served, Settings, main as preview_main

from netmon_dev.system import vm_device

from . import broker as preview_broker
from . import scan_fixtures

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "pyproject.toml").exists())
DEV_PORT = 8081
PACKAGE_LINE = "  - netmon-"
KIOSK_BRINGER = "netmon-display"
KIOSK_PACKAGE = "pihero-kiosk"


def render(sample: str, key: str) -> str:
    """Return the sample for the preview's VM: no netmon source, packages or boot-config lines; the kiosk named, since only netmon-display brought it."""
    text = device_file.drop(vm_device.render(sample, key), f"  - path: {vm_device.SOURCE}")
    text = text.replace(f"  - {KIOSK_BRINGER}\n", f"  - {KIOSK_PACKAGE}\n")
    return "".join(line for line in text.splitlines(keepends=True) if not line.startswith(PACKAGE_LINE) and "--package netmon-" not in line)


class Netmon:
    name = "netmon"
    root = ROOT
    display = (800, 480)

    def user_data(self) -> str:
        return render(vm_device.SAMPLE.read_text(), device_file.PUBLIC_KEY.read_text().strip())

    def dev_server(self, settings: Settings) -> DevServer:
        return DevServer(["./gradlew", "--console=plain", "jsBrowserDevelopmentRun", "--continuous"], DEV_PORT, {})

    def backend(self, settings: Settings) -> preview_broker.MosquittoBackend:
        broker = preview_broker.parse_broker(settings.environ.get("BROKER"))
        scan = scan_fixtures.parse_scan(settings.environ.get("SCAN") or "14+39")
        if broker.kind == preview_broker.BOARD and settings.flavor != "board":
            raise ValueError("BROKER=board is only for preview-board")
        return preview_broker.MosquittoBackend(broker, scan)

    def page_url(self, backend: preview_broker.MosquittoBackend, served: Served) -> str:
        host, port = backend.address_for(served)
        return f"http://{served.address(DEV_PORT)}/?broker.host={host}&broker.port={port}"


def main() -> int:
    return preview_main(Netmon())
