import json
import re
from pathlib import Path

import netmon_dev.assets.device_icons as device_icons
import pytest

pytestmark = pytest.mark.tier0

ROOT = next(parent for parent in Path(__file__).resolve().parents if (parent / "pyproject.toml").exists())
ASSET = ROOT / "src/jsMain/resources/assets/device-icons.json"


class TestKinds:
    def test_every_kotlin_kind_token_has_an_icon_and_nothing_else_does(self):
        source = (ROOT / "src/commonMain/kotlin/com/bkahlert/netmon/contract/Kind.kt").read_text()
        tokens = set(re.findall(r'^\s+[A-Z_]+\("(?P<token>[A-Za-z]+)"\)', source, re.M))

        assert set(device_icons.KINDS) == tokens


class TestSvg:
    def test_wraps_the_body_in_a_square_viewbox_with_the_symbol_name(self):
        data = {"prefix": "mdi", "width": 24, "height": 24, "icons": {"wifi": {"body": '<path d="M1 1" fill="currentColor"/>'}}}

        result = device_icons.svg(data, "wifi")

        assert result == '<svg xmlns="http://www.w3.org/2000/svg" data-symbol-name="mdi:wifi" viewBox="0 0 24 24"><path d="M1 1" fill="currentColor"/></svg>'

    def test_uses_the_icons_own_size_and_offset_when_given(self):
        data = {"prefix": "simple-icons", "width": 24, "height": 24, "icons": {"sonos": {"body": "<g/>", "width": 32, "height": 16, "left": -4, "top": 2}}}

        result = device_icons.svg(data, "sonos")

        assert 'viewBox="-4 -6 32 32"' in result


class TestBuild:
    def test_maps_kinds_and_matchers_to_symbol_ids_and_ships_each_used_symbol(self):
        svgs = {symbol: f"<svg>{symbol}</svg>" for symbol in device_icons.used_symbols()}

        result = device_icons.build(svgs)

        assert result["kinds"]["Television"] == "mdi:television"
        assert {"vendor": "^Sonos$", "symbol": "simple-icons:sonos"} in result["specific"]
        assert result["symbols"]["mdi:television"] == "<svg>mdi:television</svg>"
        assert set(result["symbols"]) == set(svgs)

    def test_fails_when_a_symbol_was_not_fetched(self):
        with pytest.raises(KeyError):
            device_icons.build({})


class TestGroupByPrefix:
    def test_groups_symbol_ids_by_icon_set(self):
        result = device_icons.group_by_prefix(["mdi:wifi", "simple-icons:sonos", "mdi:lan"])

        assert result == {"mdi": ["lan", "wifi"], "simple-icons": ["sonos"]}


class TestLicences:
    """Only Apache 2.0 (mdi) and CC0 1.0 (simple-icons) icons ship; a NonCommercial or ShareAlike set conflicts with the MIT licence."""

    def test_the_generator_draws_only_from_licence_clean_sets(self):
        prefixes = {symbol.split(":", 1)[0] for symbol in device_icons.used_symbols()}

        assert prefixes <= {"mdi", "simple-icons"}

    def test_the_shipped_asset_draws_only_from_licence_clean_sets(self):
        asset = json.loads(ASSET.read_text())
        prefixes = {symbol.split(":", 1)[0] for symbol in asset["symbols"]}

        assert prefixes <= {"mdi", "simple-icons"}

    def test_a_fire_tv_has_no_brand_icon_of_its_own_and_falls_back_to_its_kind(self):
        matchers = [matcher for matcher in device_icons.SPECIFIC if re.search(matcher["vendor"], "Amazon", re.I)]

        assert matchers == []


class TestSafety:
    @pytest.mark.parametrize(
        "body",
        [
            "<script>alert(1)</script>",
            '<path d="M1 1" onload="alert(1)"/>',
            '<use href="https://example.com/x.svg#a"/>',
            '<image href="https://example.com/x.png"/>',
            '<path d="M1 1" xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="https://example.com"/>',
            '<path d="M1 1" fill="url(https://example.com/x)"/>',
            "<style>*{background:url(x)}</style>",
            "<foreignObject><div/></foreignObject>",
            "<!DOCTYPE x [<!ENTITY a 'b'>]><path d='&a;'/>",
            '<path d="M1 1"',
            "<g><?a ><script>alert(1)</script>?></g>",
            '<path d="M1 1" fill="\\75rl(x)"/>',
            "<root/>",
            "</svg><path/><svg>",
        ],
    )
    def test_svg_rejects_a_hostile_body(self, body):
        data = {"prefix": "mdi", "width": 24, "height": 24, "icons": {"wifi": {"body": body}}}

        with pytest.raises(ValueError):
            device_icons.svg(data, "wifi")

    def test_svg_rejects_an_unsafe_icon_name(self):
        data = {"prefix": "mdi", "icons": {'x" onload="y': {"body": "<g/>"}}}

        with pytest.raises(ValueError):
            device_icons.svg(data, 'x" onload="y')

    @pytest.mark.parametrize(
        "markup",
        [
            '<svg xmlns="http://www.w3.org/2000/svg"><g><?a ><script>alert(1)</script>?></g></svg>',
            '<svg xmlns="http://www.w3.org/2000/svg"><path fill="\\75rl(x)"/></svg>',
            '<svg xmlns="http://www.w3.org/2000/svg"><root/></svg>',
            '<g xmlns="http://www.w3.org/2000/svg"/>',
        ],
    )
    def test_check_markup_rejects_a_hostile_svg(self, markup):
        with pytest.raises(ValueError):
            device_icons.check_markup(markup)

    def test_build_rejects_a_hostile_svg(self):
        svgs = {symbol: f'<svg xmlns="http://www.w3.org/2000/svg" data-symbol-name="{symbol}"><path d="M1 1"/></svg>' for symbol in device_icons.used_symbols()}
        svgs["mdi:wifi"] = '<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'

        with pytest.raises(ValueError):
            device_icons.build(svgs)


class TestShippedAsset:
    ALLOWED_TAGS = {"svg", "path", "g", "circle", "rect", "ellipse", "line", "polyline", "polygon"}
    TAG = re.compile(r"</?(?P<name>[^\s/>]+)")
    ATTRIBUTE = re.compile(r"\s(?P<name>[^\s=/>]+)\s*=")
    FORBIDDEN = re.compile(r"url\(|script|style|image|foreignobject|<use|<!|<\?|&|href|\\", re.I)

    @pytest.fixture
    def asset(self):
        return json.loads(ASSET.read_text())

    def test_every_tag_is_allowed(self, asset):
        tags = {match["name"] for markup in asset["symbols"].values() for match in self.TAG.finditer(markup)}

        assert tags <= self.ALLOWED_TAGS

    def test_no_attribute_is_an_event_handler(self, asset):
        attributes = {match["name"] for markup in asset["symbols"].values() for match in self.ATTRIBUTE.finditer(markup)}

        assert not {name for name in attributes if re.fullmatch(r"on\w+", name, re.I)}

    def test_no_script_style_image_use_reference_entity_or_doctype(self, asset):
        offending = [symbol for symbol, markup in asset["symbols"].items() if self.FORBIDDEN.search(markup)]

        assert offending == []

    def test_every_symbol_key_is_its_own_symbol_name(self, asset):
        names = {symbol: re.search(r'data-symbol-name="(?P<name>[^"]*)"', markup) for symbol, markup in asset["symbols"].items()}

        assert {symbol: match and match["name"] for symbol, match in names.items()} == {symbol: symbol for symbol in asset["symbols"]}

    def test_every_symbol_passes_the_generators_own_check(self, asset):
        for markup in asset["symbols"].values():
            device_icons.check_markup(markup)

    def test_every_kind_and_matcher_points_at_a_shipped_symbol(self, asset):
        referenced = {*asset["kinds"].values(), *(matcher["symbol"] for matcher in asset["specific"])}

        assert referenced <= set(asset["symbols"])
