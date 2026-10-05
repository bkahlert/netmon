import re
from pathlib import Path

import device_icons
import pytest

pytestmark = pytest.mark.tier0

ROOT = Path(__file__).resolve().parents[1]


class TestKinds:
    def test_every_kotlin_kind_token_has_an_icon_and_nothing_else_does(self):
        source = (ROOT / "src/commonMain/kotlin/com/bkahlert/netmon/Kind.kt").read_text()
        tokens = set(re.findall(r'^\s+[A-Z_]+\("([A-Za-z]+)"\)', source, re.M))

        assert set(device_icons.KINDS) == tokens


class TestSvg:
    def test_wraps_the_body_in_a_square_viewbox_with_the_symbol_name(self):
        data = {"prefix": "mdi", "width": 24, "height": 24, "icons": {"wifi": {"body": '<path d="M1 1" fill="currentColor"/>'}}}

        result = device_icons.svg(data, "wifi")

        assert result == '<svg xmlns="http://www.w3.org/2000/svg" data-symbol-name="mdi:wifi" viewBox="0 0 24 24"><path d="M1 1" fill="currentColor"/></svg>'

    def test_uses_the_icons_own_size_and_offset_when_given(self):
        data = {"prefix": "cbi", "width": 24, "height": 24, "icons": {"firetv": {"body": "<g/>", "width": 32, "height": 16, "left": -4, "top": 2}}}

        result = device_icons.svg(data, "firetv")

        assert 'viewBox="-4 -6 32 32"' in result


class TestBuild:
    def test_maps_kinds_and_matchers_to_symbol_ids_and_ships_each_used_symbol(self):
        svgs = {symbol: f"<svg>{symbol}</svg>" for symbol in device_icons.used_symbols()}

        result = device_icons.build(svgs)

        assert result["kinds"]["Television"] == "mdi:television"
        assert {"vendor": "^Amazon$", "model": "Fire TV", "symbol": "cbi:firetv"} in result["specific"]
        assert result["symbols"]["mdi:television"] == "<svg>mdi:television</svg>"
        assert set(result["symbols"]) == set(svgs)

    def test_fails_when_a_symbol_was_not_fetched(self):
        with pytest.raises(KeyError):
            device_icons.build({})


class TestGroupByPrefix:
    def test_groups_symbol_ids_by_icon_set(self):
        result = device_icons.group_by_prefix(["mdi:wifi", "cbi:firetv", "mdi:lan"])

        assert result == {"mdi": ["lan", "wifi"], "cbi": ["firetv"]}
