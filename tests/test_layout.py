from pathlib import Path

import pytest
from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import sync_playwright

import layout

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
        assert layout.outside(found["hosts"], found["scans"]) == []
        assert layout.overlapping(found["hosts"]) == []
        assert found["zoomed"] == 0

    @pytest.mark.parametrize("size", [PANEL, DESKTOP, PHONE], ids=lambda s: f"{s[0]}x{s[1]}")
    @pytest.mark.parametrize("sources", [2, 3])
    def test_scans_share_the_space_and_each_fits(self, browser, page_server, size, sources):
        found = open_page(browser, page_server, size, sources=sources, counts=(14, 39))

        assert len(found["scans"]) == sources
        assert len(found["hosts"]) == sources * 53
        assert found["scroll"]["h"] <= found["viewport"]["h"]
        assert layout.outside(found["hosts"], found["scans"]) == []
        assert layout.overlapping(found["hosts"]) == []

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

    def test_a_few_hosts_keep_their_natural_size(self, browser, page_server):
        found = open_page(browser, page_server, PANEL, sources=1, counts=(3, 0))

        assert {h["fontSize"] for h in found["hosts"]} == {NATURAL}

    def test_many_hosts_shrink_to_fit_and_the_stable_ones_less_than_the_recent(self, browser, page_server):
        found = open_page(browser, page_server, PANEL, sources=1, counts=(14, 39))

        unstable = {round(h["fontSize"], 1) for h in found["hosts"] if h["section"] == "unstable"}
        stable = {round(h["fontSize"], 1) for h in found["hosts"] if h["section"] == "stable"}
        assert max(unstable) == min(unstable) > 8
        assert max(stable) == min(stable) < max(unstable)

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


def open_page(browser, page_server, size, sources, counts):
    page = browser.new_page(viewport={"width": size[0], "height": size[1]})
    try:
        page.route_web_socket("ws://127.0.0.1:1/", layout.broker(layout.scans(sources, *counts)))
        page.goto(page_server.url)
        page.wait_for_function(f"document.querySelectorAll('.host').length >= {sources * sum(counts)}", timeout=20_000)
        return settled(page)
    finally:
        page.close()


def settled(page):
    previous, found = None, page.evaluate(layout.GEOMETRY)
    for _ in range(50):
        if found == previous:
            return found
        page.wait_for_timeout(100)
        previous, found = found, page.evaluate(layout.GEOMETRY)
    raise AssertionError("the page's layout did not settle within five seconds")
