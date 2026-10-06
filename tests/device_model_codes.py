"""Generate the scanner catalog and display model assets from device-icons on a Mac.

`make device-model-codes` writes src/jvmMain/resources/assets/model-catalog.json, mapping model codes to optional kind
tokens, and src/jsMain/resources/assets/device-model-codes.json, mapping codes to descriptions and symbol names and
symbols to SVGs. Apple model codes and symbols come from device-icons' `symbols export`; other device codes and the
symbols of Apple types that declare none are added here.
"""

from collections.abc import Iterable
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "jsMain" / "resources" / "assets" / "device-model-codes.json"
CATALOG_OUT = ROOT / "src" / "jvmMain" / "resources" / "assets" / "model-catalog.json"
DEVICE_ICONS = ["uvx", "--from", "git+https://github.com/bkahlert/device-icons@v0.3.0", "device-icons"]
# Model codes the scanner reports for Amazon and Sonos devices, and netmon's own: description and symbol name of each.
CUSTOM = {
    "MediaStick": ("Media Stick", "mediastick"),
    "FireTV": ("Fire TV", "mediastick"),
    "Fire TV": ("Fire TV", "mediastick"),
    "FireTVStick": ("Fire TV Stick", "mediastick"),
    "FireTVStick4K": ("Fire TV Stick 4K", "mediastick"),
    "Speaker": ("Speaker", "hifispeaker"),
    "WirelessSpeaker": ("Wireless Speaker", "hifispeaker"),
    "OneSL": ("One SL", "hifispeaker"),
    "One SL": ("One SL", "hifispeaker"),
}
# The symbol an Apple model code whose type declares no symbol name is drawn with, by the first pattern it matches.
FALLBACKS = (
    (re.compile(r"^Xserve"), "xserve"),
    (re.compile(r"^RackMac|^RackMount$"), "xserve.raid"),
    (re.compile(r"^PowerMac|^Tower$"), "macpro.gen1"),
)
# Every symbol named above; symbols export writes them whether or not a device type declares them.
EXTRA_SYMBOLS = sorted({symbol for _, symbol in CUSTOM.values()} | {symbol for _, symbol in FALLBACKS})
VIEW_BOX = re.compile(r'viewBox="0 0 ([\d.]+) ([\d.]+)"')


def export_commands(types: str, extras: str) -> list[list[str]]:
    """Return the two device-icons calls: every device type's symbol into types, the extra symbols into extras."""
    export = [*DEVICE_ICONS, "symbols", "export", "--no-open"]
    return [[*export, types], [*export, *(option for name in EXTRA_SYMBOLS for option in ("--symbol", name)), extras]]


def build(index: dict, svgs: dict[str, str]) -> dict:
    """Return the JSON data: models from the index's types, the dropped codes, and CUSTOM; symbols, squared, for those that get one.

    A model code whose type declares no symbol name, or a name svgs lacks, gets the first FALLBACKS match, or None.
    Models and symbols are sorted.
    """
    models = {}
    for declaration in index["types"].values():
        for code in declaration["model_identifiers"]:
            symbol = declaration["symbol_name"] if declaration["symbol_name"] in svgs else fallback(code)
            models[code] = {"description": declaration["description"], "symbol": symbol}
    for code in index["dropped"]["no type"]:
        models.setdefault(code, {"description": None, "symbol": None})
    for code, (description, symbol) in CUSTOM.items():
        models[code] = {"description": description, "symbol": symbol}
    used = sorted({model["symbol"] for model in models.values() if model["symbol"] is not None})
    return {"models": dict(sorted(models.items())), "symbols": {name: square(svgs[name], name) for name in used}}


def build_catalog(model_codes: Iterable[str], existing: dict) -> dict:
    """Return explicit kinds for the generated model codes, leaving new codes unclassified."""
    existing_models = existing.get("models", {})
    return {"models": {code: existing_models.get(code) for code in sorted(set(model_codes))}}


def fallback(code: str) -> str | None:
    """Return the symbol name of the first FALLBACKS pattern the model code matches, or None."""
    return next((symbol for pattern, symbol in FALLBACKS if pattern.search(code)), None)


def square(svg: str, name: str) -> str:
    """Return the SVG with its tight viewBox widened to a square around its centre and a data-symbol-name of name.

    Every symbol then fills the same box in the display. Raises ValueError without a `viewBox="0 0 w h"`.
    """
    match = VIEW_BOX.search(svg)
    if match is None:
        raise ValueError(f"{name}: no viewBox of the form 0 0 w h")
    width, height = float(match[1]), float(match[2])
    side = max(width, height)
    view_box = " ".join(number(value) for value in ((width - side) / 2, (height - side) / 2, side, side))
    return svg.replace(match[0], f'data-symbol-name="{name}" viewBox="{view_box}"', 1)


def number(value: float) -> str:
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def render(data: dict) -> str:
    """Return the JSON text, indented, ending with a newline."""
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def main(argv: list[str]) -> int:
    if argv:
        print("usage: python tests/device_model_codes.py", file=sys.stderr)
        return 2
    if sys.platform != "darwin":
        print("device-model-codes is macOS only: device-icons reads CoreTypes.bundle and CoreGlyphs.bundle", file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory() as tmp:
        types, extras = Path(tmp) / "types", Path(tmp) / "extras"
        for command in export_commands(str(types), str(extras)):
            if subprocess.run(command, check=False).returncode:
                raise SystemExit(f"device-icons failed: {' '.join(command)}")
        index = json.loads((types / "index.json").read_text())
        svgs = {path.stem: path.read_text() for out in (types, extras) for path in (out / "symbols").glob("*.svg")}
    data = build(index, svgs)
    catalog = build_catalog(data["models"], json.loads(CATALOG_OUT.read_text()))
    OUT.write_text(render(data))
    CATALOG_OUT.write_text(render(catalog))
    print(f"{len(data['models'])} model codes, {len(data['symbols'])} symbols, {len(catalog['models'])} catalog entries")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
