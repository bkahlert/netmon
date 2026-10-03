"""The preview's settings: its flavor and the variables every flavor takes."""
from dataclasses import dataclass

import preview_broker
import preview_kiosk
import scan_fixtures

FLAVORS = ("browser", "vm", "device")


@dataclass(frozen=True)
class Settings:
    flavor: str
    scan: scan_fixtures.Scan
    broker: preview_broker.Broker
    inspect: str | None
    target: str | None = None

    @staticmethod
    def from_environ(flavor: str, environ) -> "Settings":
        if flavor not in FLAVORS:
            raise ValueError(f"flavor must be browser, vm or device, not {flavor!r}")
        scan = scan_fixtures.parse_scan(environ.get("SCAN") or "14+39")
        broker = preview_broker.parse_broker(environ.get("BROKER"))
        target = environ.get("TARGET") or None
        if broker.kind == preview_broker.DEVICE and flavor != "device":
            raise ValueError("BROKER=device is only for preview-device")
        if flavor == "device" and not target:
            raise ValueError("preview-device needs TARGET=user@host")
        if flavor != "device" and target:
            raise ValueError("TARGET is only for preview-device")
        return Settings(flavor, scan, broker, preview_kiosk.inspect_app(environ.get("INSPECT", "Safari")), target)
