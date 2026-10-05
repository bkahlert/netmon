"""The scans the preview publishes and the layout test serves: one definition for both."""
import re
import time
from typing import NamedTuple

VENDORS = ["Apple", "Espressif", "Raspberry Pi Foundation", "AVM Audiovisuelles Marketing und Computersysteme GmbH", None, "HP"]
NAMES = ["printer.local.", "NPI000001", None, "sample-node-(690).local.", "indoorcam", "525400000000AB00.local.", "Zed"]
MODELS = ["Mac14,8", None, "AppleTV3,2", "AirPort10,115", None, "AirPods3,1"]
KINDS = ["Computer", "Printer", None, "Camera", "Smartphone", "Socket", "SetTopBox", None]
LINKS = [("ethernet", 1000), ("wifi", 866), None, ("wifi", 72), ("ethernet", 2500), ("wifi", None), None]
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


KINDS_SCAN_TOPIC = "dt/netmon/node/wlan0/192.0.2.0/24/scan"
KINDS_HOSTS = [
    ("192.0.2.200", "Router", "gateway", "up", 2 * 86400),
    ("192.0.2.3", "NetworkSwitch", "switch", "up", 13 * 3600),
    ("192.0.2.10", "Laptop", "notebook", "up", 30),
    ("192.0.2.9", "Computer", "desktop", "down", 600),
    ("192.0.2.40", "Smartphone", "handset", "up", 6 * 60),
    ("192.0.2.4", "Tablet", "slate", "up", 30 * 60),
    ("192.0.2.60", "Television", "screen", "up", 2 * 3600),
    ("192.0.2.6", "Speaker", "boombox", "up", 20 * 3600),
    ("192.0.2.70", "Socket", "plug", "up", 3 * 86400),
    ("192.0.2.7", "Hub", "bridge", "up", 90),
    ("192.0.2.80", None, None, "up", 25 * 60),
    ("192.0.2.8", "Generic", "gadget", "down", 7200),
]


def kinds_scan(now: int | None = None) -> dict[str, dict]:
    """Returns one scan with hosts of each group of kinds, two per group, their addresses out of order and their times up in every step the display highlights."""
    now = now or int(time.time())
    hosts = []
    for ip, kind, name, status, age in KINDS_HOSTS:
        entry = {"ip": ip, "status": status, "since": now - age}
        if kind:
            entry["kind"] = kind
        if name:
            entry["name"] = name
        hosts.append(entry)
    return {KINDS_SCAN_TOPIC: {"event": "scan", "type": "completed", "hosts": hosts, "timestamp": now - 1}}


def host(source: int, index: int, since: int) -> dict:
    entry = {"ip": f"10.{source}.{index // 250}.{index % 250 + 1}", "status": "down" if index % 7 == 3 else "up", "since": since}
    for key, values in (("name", NAMES), ("vendor", VENDORS), ("model", MODELS)):
        value = values[index % len(values)]
        if value:
            entry[key] = value
    kind = KINDS[index % len(KINDS)]
    if kind:
        entry["kind"] = kind
    link = LINKS[index % len(LINKS)]
    if link:
        entry["link"], speed = link
        if speed:
            entry["speed"] = speed
    return entry
