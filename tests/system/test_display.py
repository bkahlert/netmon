import re
from pathlib import Path

import pytest
from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

from netmon_dev.system.booted import Tunnel, exactly, gateway_of, journal_until

pytestmark = [pytest.mark.boot, pytest.mark.requires_webkit]
PANEL = {"width": 800, "height": 480}


class TestDisplay:
    def test_shows_the_gateway_the_scanner_found(self, host, page, tunnel, screenshot):
        log = journal_until(host, "completed and published to", attempts=90)
        assert "completed and published to" in log
        gateway = gateway_of(host.check_output("ip -4 route show default"))

        page.goto(f"http://127.0.0.1:{tunnel.http}/?broker.host=127.0.0.1&broker.port={tunnel.ws}")
        shown = page.locator('.host[data-status="up"]').filter(has=page.locator(".font-mono", has_text=exactly(gateway)))
        try:
            shown.first.wait_for(timeout=120_000)
        except PlaywrightTimeoutError:
            pytest.fail(f"{gateway} is not shown as up; hosts on the page: {page.locator('.host .font-mono').all_inner_texts()}")
        page.screenshot(path=str(screenshot))

        assert shown.count() == 1
        assert screenshot.stat().st_size > 0

    def test_shows_the_kiosks_load_in_the_status_bar(self, page, tunnel):
        page.goto(f"http://127.0.0.1:{tunnel.http}/?broker.host=127.0.0.1&broker.port={tunnel.ws}")

        pill = page.locator(".status span", has_text=re.compile(r"^web \d+ %$"))

        pill.first.wait_for(timeout=20_000)
        assert page.locator(".status span", has_text=re.compile(r"^kiosk \d+ MB$")).count() == 1


@pytest.fixture(scope="module")
def tunnel(target):
    tunnel = Tunnel(target)
    yield tunnel
    tunnel.close()


@pytest.fixture(scope="module")
def page():
    with sync_playwright() as playwright:
        try:
            browser = playwright.webkit.launch()
        except PlaywrightError as e:
            pytest.fail(f"Playwright's WebKit is not installed; run `make browser`\n{e}")
        page = browser.new_page(viewport=PANEL)
        yield page
        browser.close()


@pytest.fixture(scope="module")
def screenshot(request):
    target = request.config.getoption("--target")
    path = Path.cwd() / "dist" / ("tier2" if target == "vm" else target) / "display.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path
