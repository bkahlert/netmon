"""The scans the preview publishes and the layout test serves: one definition for both."""
import re
import time
from typing import NamedTuple

VENDORS = ["Apple", "Espressif", "Raspberry Pi Foundation", "AVM Audiovisuelles Marketing und Computersysteme GmbH", None, "HP"]
NAMES = ["printer.local.", "NPID96FF6", None, "openclaw-(690).local.", "indoorcam", "52540003C3310000.local.", "Shi"]
MODELS = ["Mac14,8", None, "AppleTV3,2", "AirPort10,115", None, "AirPods3,1"]
SCAN = re.compile(r"(?P<recent>\d+)\+(?P<stable>\d+)(?:x(?P<sources>\d+))?")


class Scan(NamedTuple):
    recent: int
    stable: int
    sources: int


def parse_scan(text: str) -> Scan:
    """Returns the fixture `text` names: `14+39` is 14 recent and 39 stable hosts in one scan, `14+39x2` the same in two scans."""
    match = SCAN.fullmatch(text)
    if not match or match["sources"] == "0":
        raise ValueError(f"SCAN must be RECENT+STABLE or RECENT+STABLExSCANS with at least one scan, not {text!r}")
    return Scan(int(match["recent"]), int(match["stable"]), int(match["sources"] or 1))


def scans(sources: int, recent: int, stable: int, now: int | None = None) -> dict[str, dict]:
    now = now or int(time.time())
    result = {}
    for s in range(sources):
        topic = "dt/netmon/node/wlan0/10.0.0.1/24/scan" if s == 0 else f"dt/netmon/node{s}/eth{s}/10.{s}.0.1/24/scan"
        hosts = [host(s, i, now - 30 - i) for i in range(recent)] + [host(s, recent + i, now - 7200 - i) for i in range(stable)]
        result[topic] = {"event": "scan", "type": "completed", "hosts": hosts, "timestamp": now - 1}
    return result


def host(source: int, index: int, since: int) -> dict:
    entry = {"ip": f"10.{source}.{index // 250}.{index % 250 + 1}", "status": "down" if index % 7 == 3 else "up", "since": since}
    for key, values in (("name", NAMES), ("vendor", VENDORS), ("model", MODELS)):
        value = values[index % len(values)]
        if value:
            entry[key] = value
    return entry
