import json

import device_model_codes
import pytest

pytestmark = pytest.mark.tier0


class TestExportCommands:
    def test_export_every_device_type_then_the_extra_symbols(self):
        commands = device_model_codes.export_commands("/tmp/types", "/tmp/extras")

        assert commands == [
            [*device_model_codes.DEVICE_ICONS, "symbols", "export", "--no-open", "/tmp/types"],
            [*device_model_codes.DEVICE_ICONS, "symbols", "export", "--no-open", *symbol_options(device_model_codes.EXTRA_SYMBOLS), "/tmp/extras"],
        ]


class TestBuild:
    def test_maps_each_model_code_of_a_type_to_its_description_and_symbol(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert result["models"]["MacPro7,1"] == {"description": "Mac Pro", "symbol": "macpro.gen3"}
        assert result["models"]["MacPro7,1@ECOLOR=226,226,224"] == {"description": "Mac Pro", "symbol": "macpro.gen3"}

    def test_keeps_a_description_the_type_lacks_as_null(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert result["models"]["iPhone19,4"]["description"] is None

    def test_knows_a_model_code_no_type_declares_without_description_or_symbol(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert result["models"]["AppleDisplay18,2"] == {"description": None, "symbol": None}

    def test_leaves_the_symbol_null_when_the_symbol_name_has_no_svg(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert result["models"]["iPhone19,4"]["symbol"] is None

    def test_adds_the_custom_model_codes(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert result["models"]["FireTVStick4K"] == {"description": "Fire TV Stick 4K", "symbol": "mediastick"}
        assert result["models"]["One SL"] == {"description": "One SL", "symbol": "hifispeaker"}

    def test_ships_each_symbol_a_model_code_gets_squared(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert result["symbols"]["macpro.gen3"] == device_model_codes.square(SVGS["macpro.gen3"], "macpro.gen3")

    def test_leaves_out_a_symbol_no_model_code_gets(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert "display" not in result["symbols"]

    def test_sorts_the_models_and_the_symbols(self):
        result = device_model_codes.build(INDEX, SVGS)

        assert list(result["models"]) == sorted(result["models"])
        assert list(result["symbols"]) == sorted(result["symbols"])

    class TestOnFallbacks:
        def test_draws_an_xserve_whose_type_has_no_symbol_name_as_xserve(self):
            result = device_model_codes.build(INDEX, SVGS)

            assert result["models"]["Xserve3,1"] == {"description": "Xserve", "symbol": "xserve"}
            assert "xserve" in result["symbols"]

        def test_draws_a_rack_mac_as_xserve_raid_and_a_power_mac_as_the_first_mac_pro(self):
            result = device_model_codes.build(INDEX, SVGS)

            assert result["models"]["RackMac1,1"]["symbol"] == "xserve.raid"
            assert result["models"]["PowerMac7,2"]["symbol"] == "macpro.gen1"

        def test_leaves_the_symbol_null_without_a_matching_fallback(self):
            result = device_model_codes.build(INDEX, SVGS)

            assert result["models"]["Macintosh"]["symbol"] is None


class TestBuildCatalog:
    def test_presentation_symbols_do_not_change_classification(self):
        codes = device_model_codes.build(INDEX, SVGS)["models"]
        changed_svgs = {name: svg.replace('fill="currentColor"', 'fill="red"') for name, svg in SVGS.items()}
        changed_codes = device_model_codes.build(INDEX, changed_svgs)["models"]

        assert device_model_codes.build_catalog(codes, EXISTING_CATALOG) == device_model_codes.build_catalog(changed_codes, EXISTING_CATALOG)

    def test_existing_explicit_kinds_survive_regeneration(self):
        result = device_model_codes.build_catalog(["MacPro7,1", "MacProNew1,1"], EXISTING_CATALOG)

        assert result == {"models": {"MacPro7,1": "Computer", "MacProNew1,1": None}}

    def test_new_models_have_no_implicit_kind(self):
        result = device_model_codes.build_catalog(["NewApple2,1"], EXISTING_CATALOG)

        assert result["models"]["NewApple2,1"] is None

    def test_membership_follows_generated_model_identifiers_not_symbol_availability(self):
        codes = device_model_codes.build(INDEX, SVGS)["models"]
        result = device_model_codes.build_catalog(codes, EXISTING_CATALOG)

        assert set(result["models"]) == set(codes)
        assert "iPhone19,4" in result["models"]


class TestSquare:
    def test_pads_a_tall_symbol_to_a_centred_square(self):
        result = device_model_codes.square('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 81.53 109.97"><path d="M0 0"/></svg>', "ipad")

        assert 'viewBox="-14.22 0 109.97 109.97"' in result

    def test_pads_a_wide_symbol_to_a_centred_square(self):
        result = device_model_codes.square('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 40"><path d="M0 0"/></svg>', "xserve.raid")

        assert 'viewBox="0 -40 120 120"' in result

    def test_names_the_symbol(self):
        result = device_model_codes.square('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path d="M0 0"/></svg>', "pc")

        assert result.startswith('<svg xmlns="http://www.w3.org/2000/svg" data-symbol-name="pc" viewBox="0 0 10 10">')

    def test_refuses_an_svg_without_a_tight_view_box(self):
        with pytest.raises(ValueError, match="viewBox"):
            device_model_codes.square('<svg xmlns="http://www.w3.org/2000/svg" width="10"><path d="M0 0"/></svg>', "pc")


class TestRender:
    def test_is_json_with_models_then_symbols_and_a_final_newline(self):
        result = device_model_codes.render(device_model_codes.build(INDEX, SVGS))

        assert list(json.loads(result)) == ["models", "symbols"]
        assert result.endswith("}\n")


INDEX = {
    "symbols": {},
    "types": {
        "com.apple.macpro-2019": {
            "description": "Mac Pro",
            "symbol_name": "macpro.gen3",
            "model_identifiers": ["MacPro7,1", "MacPro7,1@ECOLOR=226,226,224"],
        },
        "com.apple.iphone-private": {"description": None, "symbol_name": "private.name", "model_identifiers": ["iPhone19,4"]},
        "com.apple.xserve-xeon": {"description": "Xserve", "symbol_name": None, "model_identifiers": ["Xserve3,1"]},
        "com.apple.xserve": {"description": "Xserve", "symbol_name": None, "model_identifiers": ["RackMac1,1"]},
        "com.apple.powermac": {"description": "Power Mac", "symbol_name": None, "model_identifiers": ["PowerMac7,2"]},
        "com.apple.mac": {"description": "Mac", "symbol_name": None, "model_identifiers": ["Macintosh"]},
    },
    "dropped": {"no type": ["AppleDisplay18,2"], "no symbol name": ["Macintosh", "PowerMac7,2", "RackMac1,1", "Xserve3,1"], "no symbol": ["iPhone19,4"]},
}
EXISTING_CATALOG = {"models": {"MacPro7,1": "Computer", "FireTV": None}}
SVGS = {
    name: f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} 100"><path fill="currentColor" d="M0 0L{width} 100Z"/></svg>'
    for name, width in [("macpro.gen3", "82"), ("xserve", "120"), ("xserve.raid", "120"), ("macpro.gen1", "90"), ("mediastick", "40"), ("hifispeaker", "60"), ("display", "100")]
}


def symbol_options(names):
    return [option for name in names for option in ("--symbol", name)]
