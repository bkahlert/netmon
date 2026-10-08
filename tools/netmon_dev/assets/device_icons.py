"""The kind and brand icons the display draws, fetched from the Iconify API.

`make device-icons` writes src/jsMain/resources/assets/device-icons.json: `kinds` maps each Kind token to a symbol
id, `specific` lists vendor/model/name regex matchers to a symbol id (first match wins), `symbols` maps each symbol id
to its SVG. Kinds and device shapes come from Material Design Icons (Apache 2.0), vendor wordmarks from Simple Icons (CC0 1.0).
"""

import json
import re
import sys
import urllib.request
import xml.etree.ElementTree as ElementTree
from pathlib import Path

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "pyproject.toml").exists())
OUT = ROOT / "src" / "jsMain" / "resources" / "assets" / "device-icons.json"
API = "https://api.iconify.design"

KINDS = {
    "AirConditioner": "mdi:air-conditioner",
    "AirPurifier": "mdi:air-purifier",
    "Button": "mdi:light-switch",
    "Camera": "mdi:cctv",
    "CircuitBoard": "mdi:chip",
    "Computer": "mdi:desktop-tower",
    "DoorBell": "mdi:doorbell-video",
    "DoorLock": "mdi:lock",
    "GamingDevice": "mdi:controller",
    "Generic": "mdi:devices",
    "Hub": "mdi:hub-outline",
    "IPPhone": "mdi:phone-classic",
    "Lamp": "mdi:lightbulb",
    "Laptop": "mdi:laptop",
    "Monitor": "mdi:monitor",
    "NetworkSwitch": "mdi:lan",
    "Phone": "mdi:phone",
    "Printer": "mdi:printer",
    "Robot": "mdi:robot-vacuum",
    "Router": "mdi:router-wireless",
    "Sensor": "mdi:motion-sensor",
    "SetTopBox": "mdi:cast",
    "Shutter": "mdi:window-shutter",
    "SmartWatch": "mdi:watch",
    "Smartphone": "mdi:cellphone",
    "Socket": "mdi:power-socket-eu",
    "Speaker": "mdi:speaker",
    "Storage": "mdi:nas",
    "Tablet": "mdi:tablet",
    "Television": "mdi:television",
    "Thermostat": "mdi:thermostat",
}
# Matchers are regexes, case-insensitive, over the resolved vendor, model and name; every given field must match.
SPECIFIC = [
    {"vendor": "^Sonos$", "symbol": "simple-icons:sonos"},
    {"vendor": "^Signify$", "symbol": "simple-icons:philipshue"},
    {"vendor": "^Ring$", "symbol": "simple-icons:ring"},
    {"vendor": "^tado$", "symbol": "simple-icons:tado"},
    {"vendor": "^AVM$", "symbol": "simple-icons:avm"},
    {"vendor": "^Raspberry Pi$", "symbol": "mdi:raspberry-pi"},
    {"vendor": "^Nintendo$", "symbol": "mdi:nintendo-switch"},
]
EXTRA = {"ethernet": "mdi:ethernet", "wifi": "mdi:wifi"}

# The display inlines these SVGs into its DOM, so only plain shapes and presentation attributes pass.
SVG_NAMESPACE = "{http://www.w3.org/2000/svg}"
ALLOWED_TAGS = {"svg", "path", "g", "circle", "rect", "ellipse", "line", "polyline", "polygon"}
ALLOWED_ATTRIBUTES = {
    "xmlns", "viewBox", "data-symbol-name", "d", "points", "transform", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry",
    "width", "height", "fill", "fill-rule", "fill-opacity", "clip-rule", "opacity", "stroke", "stroke-width", "stroke-linecap",
    "stroke-linejoin", "stroke-miterlimit", "stroke-opacity",
}
SAFE_NAME = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")


def used_symbols() -> list[str]:
    """Return every symbol id the asset needs, sorted."""
    return sorted({*KINDS.values(), *(matcher["symbol"] for matcher in SPECIFIC), *EXTRA.values()})


def group_by_prefix(symbols: list[str]) -> dict[str, list[str]]:
    """Return icon names per icon set prefix, each list sorted."""
    groups: dict[str, list[str]] = {}
    for symbol in symbols:
        prefix, name = symbol.split(":", 1)
        groups.setdefault(prefix, []).append(name)
    return {prefix: sorted(names) for prefix, names in groups.items()}


def fetch(prefix: str, names: list[str]) -> dict:
    """Return the Iconify JSON of the named icons of one set."""
    # The API answers 403 to Python's default user agent.
    request = urllib.request.Request(f"{API}/{prefix}.json?icons={','.join(names)}", headers={"User-Agent": "netmon-device-icons"})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.load(response)
    missing = data.get("not_found", [])
    if missing:
        raise SystemExit(f"{prefix}: icons not found: {', '.join(missing)}")
    return data


def check_markup(markup: str) -> None:
    """Raise ValueError unless the markup is one SVG with only allowed tags and attributes and no tricks that parse differently in a browser."""
    if "<!" in markup or "<?" in markup or "&" in markup or "\\" in markup or "url(" in markup.lower():
        raise ValueError(f"disallowed construct in SVG: {markup[:80]}")
    try:
        root = ElementTree.fromstring(markup)
    except ElementTree.ParseError as error:
        raise ValueError(f"SVG does not parse: {error}") from error
    if root.tag.removeprefix(SVG_NAMESPACE) != "svg":
        raise ValueError(f"not an SVG: {root.tag}")
    for element in root.iter():
        if element.tag.removeprefix(SVG_NAMESPACE) not in ALLOWED_TAGS:
            raise ValueError(f"disallowed SVG tag: {element.tag}")
        for attribute in element.attrib:
            if attribute not in ALLOWED_ATTRIBUTES:
                raise ValueError(f"disallowed SVG attribute: {attribute}")


def svg(data: dict, name: str) -> str:
    """Return the icon as a standalone SVG with a square viewBox around its box and a data-symbol-name.

    Raises ValueError for a name or body that is not plain, safe SVG.
    """
    if not SAFE_NAME.fullmatch(data["prefix"]) or not SAFE_NAME.fullmatch(name):
        raise ValueError(f"unsafe icon name: {data['prefix']}:{name}")
    icon = data["icons"][name]
    width = icon.get("width", data.get("width", 16))
    height = icon.get("height", data.get("height", 16))
    left = icon.get("left", data.get("left", 0))
    top = icon.get("top", data.get("top", 0))
    side = max(width, height)
    view_box = f"{number(left - (side - width) / 2)} {number(top - (side - height) / 2)} {number(side)} {number(side)}"
    markup = f'<svg xmlns="http://www.w3.org/2000/svg" data-symbol-name="{data["prefix"]}:{name}" viewBox="{view_box}">{icon["body"]}</svg>'
    check_markup(markup)
    return markup


def number(value: float) -> str:
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def build(svgs: dict[str, str]) -> dict:
    """Return the JSON data; raises KeyError for a symbol svgs lacks and ValueError for an unsafe SVG."""
    for symbol in used_symbols():
        check_markup(svgs[symbol])
    return {
        "kinds": dict(sorted(KINDS.items())),
        "specific": SPECIFIC,
        "symbols": {symbol: svgs[symbol] for symbol in used_symbols()},
    }


def render(data: dict) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main(argv: list[str]) -> int:
    if argv:
        print("usage: python -m netmon_dev.assets.device_icons", file=sys.stderr)
        return 2
    svgs = {}
    for prefix, names in group_by_prefix(used_symbols()).items():
        data = fetch(prefix, names)
        svgs.update({f"{prefix}:{name}": svg(data, name) for name in names})
    data = build(svgs)
    OUT.write_text(render(data))
    print(f"{len(data['kinds'])} kinds, {len(data['specific'])} matchers, {len(data['symbols'])} symbols in {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
