from pathlib import Path

import pytest
from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import sync_playwright

import layout
import scan_fixtures

pytestmark = pytest.mark.layout
DIST = Path.cwd() / "build" / "dist" / "js" / "productionExecutable"
PANEL = (800, 480)
DESKTOP = (1440, 900)
PHONE = (390, 844)
NATURAL = 16.0


class TestLayout:
    @pytest.mark.parametrize("size", [PANEL, DESKTOP, PHONE], ids=lambda s: f"{s[0]}x{s[1]}")
    @pytest.mark.parametrize("counts", [(1, 0), (0, 5), (3, 0), (14, 39), (53, 0), (60, 60)], ids=lambda c: f"{c[0]}+{c[1]}")
    def test_every_host_fits_its_scan_without_scrolling(self, browser, page_server, size, counts):
        found = open_page(browser, page_server, size, sources=1, counts=counts)

        assert len(found["hosts"]) == sum(counts)
        assert found["scroll"]["h"] <= found["viewport"]["h"]
        assert found["scroll"]["w"] <= found["viewport"]["w"]
        assert layout.outside(found["hosts"] + found["labels"], found["scans"]) == []
        assert layout.overlapping(found["hosts"] + found["labels"]) == []
        assert found["zoomed"] == 0

    @pytest.mark.parametrize("size", [PANEL, DESKTOP, PHONE], ids=lambda s: f"{s[0]}x{s[1]}")
    @pytest.mark.parametrize("sources", [2, 3])
    def test_scans_share_the_space_and_each_fits(self, browser, page_server, size, sources):
        found = open_page(browser, page_server, size, sources=sources, counts=(14, 39))

        assert len(found["scans"]) == sources
        assert len(found["hosts"]) == sources * 53
        assert found["scroll"]["h"] <= found["viewport"]["h"]
        assert layout.outside(found["hosts"] + found["labels"], found["scans"]) == []
        assert layout.overlapping(found["hosts"] + found["labels"]) == []

    def test_a_slow_page_is_measured_once_it_has_rendered(self, browser, page_server):
        found = open_page(browser, page_server, PANEL, sources=3, counts=(14, 39), slowdown=1)

        assert layout.outside(found["hosts"] + found["labels"], found["scans"]) == []
        assert layout.overlapping(found["hosts"] + found["labels"]) == []

    def test_the_utility_classes_of_the_kotlin_code_are_styled(self, browser, page_server):
        found = open_page(browser, page_server, PANEL, sources=1, counts=(3, 0))

        assert found["card"]["borderTopWidth"] == "1px"
        assert found["card"]["borderTopLeftRadius"] != "0px"

    def test_the_loading_animation_ends(self, browser, page_server):
        page = browser.new_page(viewport={"width": PANEL[0], "height": PANEL[1]})
        try:
            page.goto(page_server.url_of(page_server.loading_image()))
            iterations = page.evaluate("document.getAnimations().map(a => a.effect.getComputedTiming().iterations)")
        finally:
            page.close()

        assert iterations
        assert all(count != float("inf") for count in iterations), iterations

    def test_no_host_card_is_animated(self, browser, page_server):
        page = browser.new_page(viewport={"width": PANEL[0], "height": PANEL[1]})
        try:
            scans = scan_fixtures.kinds_scan()
            page.route_web_socket("ws://127.0.0.1:1/", layout.broker(scans))
            page.goto(page_server.url)
            page.wait_for_function(SINCE_SHOWN, arg=len(scan_fixtures.KINDS_HOSTS), timeout=20_000)
            animated = page.evaluate(ANIMATIONS_AFTER_TWO_FRAMES)
        finally:
            page.close()

        assert animated == 0

    def test_each_step_of_time_online_has_a_style_of_its_own_and_the_oldest_none(self, browser, page_server):
        page = browser.new_page(viewport={"width": PANEL[0], "height": PANEL[1]})
        try:
            page.route_web_socket("ws://127.0.0.1:1/", layout.broker(scan_fixtures.kinds_scan()))
            page.goto(page_server.url)
            page.wait_for_function(SINCE_SHOWN, arg=len(scan_fixtures.KINDS_HOSTS), timeout=20_000)
            page.wait_for_function("document.querySelectorAll('.host[data-age]').length === 10", timeout=5_000)
            styles = page.evaluate(STEP_STYLES)
        finally:
            page.close()

        by_step = {card["age"]: (card["background"], card["shadow"]) for card in styles if card["age"]}
        baseline = ("rgba(0, 0, 0, 0)", "none")
        assert list(by_step) == ["5m", "20m", "1h", "12h", "24h", "older"]
        assert len(set(by_step.values())) == 6
        assert by_step["older"] == baseline
        assert {(card["background"], card["shadow"]) for card in styles if not card["age"]} == {baseline}

    def test_a_few_hosts_keep_their_natural_size(self, browser, page_server):
        found = open_page(browser, page_server, PANEL, sources=1, counts=(3, 0))

        assert {h["fontSize"] for h in found["hosts"]} == {NATURAL}

    def test_many_hosts_shrink_to_one_size_that_stays_legible(self, browser, page_server):
        found = open_page(browser, page_server, PANEL, sources=1, counts=(14, 39))

        sizes = {round(h["fontSize"], 1) for h in found["hosts"]}
        assert len(sizes) == 1
        assert min(sizes) >= 6.9

    @pytest.mark.parametrize("size", [PANEL, DESKTOP, PHONE], ids=lambda s: f"{s[0]}x{s[1]}")
    def test_the_groups_read_in_order_each_after_its_label(self, browser, page_server, size):
        found = open_page(browser, page_server, size, scans=scan_fixtures.kinds_scan())

        assert layout.reading_order(found["cells"]) == [
            "Network", "192.0.2.3", "192.0.2.200",
            "Computers", "192.0.2.9", "192.0.2.10",
            "Phones & tablets", "192.0.2.4", "192.0.2.40",
            "Media", "192.0.2.6", "192.0.2.60",
            "Smart home", "192.0.2.7", "192.0.2.70",
            "Other", "192.0.2.8", "192.0.2.80",
        ]
        assert found["scroll"]["h"] <= found["viewport"]["h"]
        assert found["scroll"]["w"] <= found["viewport"]["w"]
        assert layout.outside(found["hosts"] + found["labels"], found["scans"]) == []
        assert layout.overlapping(found["hosts"] + found["labels"]) == []

    @pytest.mark.parametrize("size", [PANEL, DESKTOP, PHONE], ids=lambda s: f"{s[0]}x{s[1]}")
    def test_a_label_is_as_wide_as_the_cards_and_no_taller(self, browser, page_server, size):
        found = open_page(browser, page_server, size, sources=1, counts=(14, 39))

        heights = {round(label["b"] - label["t"]) for label in found["labels"]}
        widths = {round(label["r"] - label["l"]) for label in found["labels"]} | {round(h["r"] - h["l"]) for h in found["hosts"]}
        assert len(found["labels"]) == 5
        assert len(widths) == 1
        assert max(heights) <= max(round(h["b"] - h["t"]) for h in found["hosts"])

    def test_many_hosts_use_the_panel_down_to_its_lower_part(self, browser, page_server):
        found = open_page(browser, page_server, PANEL, sources=1, counts=(14, 39))

        lowest = max(h["b"] for h in found["hosts"])
        assert lowest > 0.8 * found["viewport"]["h"]


@pytest.fixture(scope="module")
def page_server():
    if not list(DIST.glob("netmon.*.js")):
        pytest.fail(f"{DIST} has no page; run `./gradlew jsBrowserDistribution` (make test-layout does)")
    server = layout.Page(DIST)
    yield server
    server.close()


@pytest.fixture(scope="module")
def browser():
    with sync_playwright() as playwright:
        try:
            engine = playwright.webkit.launch()
        except PlaywrightError as e:
            pytest.fail(f"Playwright's WebKit is not installed; run `make browser`\n{e}")
        yield engine
        engine.close()


SINCE_SHOWN = "count => [...document.querySelectorAll('.host__status')].filter(s => s.textContent.includes('since')).length === count"
ANIMATIONS_AFTER_TWO_FRAMES = """new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() =>
    resolve([...document.querySelectorAll('.hosts')].flatMap(e => e.getAnimations({ subtree: true })).length))))"""


STEP_STYLES = """[...document.querySelectorAll('.host')]
    .map(h => ({age: h.dataset.age, background: getComputedStyle(h).backgroundColor, shadow: getComputedStyle(h).boxShadow}))
    .sort((a, b) => ['5m', '20m', '1h', '12h', '24h', 'older'].indexOf(a.age) - ['5m', '20m', '1h', '12h', '24h', 'older'].indexOf(b.age))"""


def open_page(browser, page_server, size, sources=1, counts=(0, 0), slowdown=0, scans=None):
    page = browser.new_page(viewport={"width": size[0], "height": size[1]})
    try:
        if slowdown:
            page.add_init_script(layout.slowed(slowdown))
        scans = scans or scan_fixtures.scans(sources, *counts)
        page.route_web_socket("ws://127.0.0.1:1/", layout.broker(scans))
        page.goto(page_server.url)
        expected = {"hosts": sum(len(scan["hosts"]) for scan in scans.values()), "models": sum("model" in host for scan in scans.values() for host in scan["hosts"]), "links": sum("link" in host for scan in scans.values() for host in scan["hosts"])}
        page.wait_for_function(layout.RENDERED, arg=expected, timeout=20_000)
        return page.evaluate(layout.GEOMETRY)
    finally:
        page.close()
