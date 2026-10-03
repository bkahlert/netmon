import json
import re
import shlex
import time

import pytest

pytestmark = pytest.mark.installed
FETCH = "python3 -c 'import urllib.request,sys; print(urllib.request.urlopen(sys.argv[1], timeout=5).read().decode())' "
HEADERS = "python3 -c 'import urllib.request,sys; print(urllib.request.urlopen(urllib.request.Request(sys.argv[1], method=\"HEAD\"), timeout=5).headers)' "
BUNDLE = re.compile(r'src="(netmon\.[0-9a-f]{8}\.js)"')
LOADING = re.compile(r'(images/loading\.[0-9a-f]{8}\.svg)')


class TestPackage:
    def test_is_installed_at_the_built_version(self, host, version):
        package = host.package("netmon-display")

        assert package.is_installed
        assert package.version == version

    def test_pulls_in_lighttpd_and_the_kiosk(self, host):
        assert host.package("lighttpd").is_installed
        assert host.package("pihero-kiosk").is_installed


class TestServing:
    def test_the_display_answers_at_the_root(self, host):
        page = fetch_until(host, "http://localhost/", "Netmon Web Display")

        assert "<title>Netmon Web Display</title>" in page
        assert BUNDLE.search(page), page

    def test_the_bundle_is_served(self, host):
        page = fetch_until(host, "http://localhost/", "Netmon Web Display")

        bundle = host.check_output(FETCH + "http://localhost/" + BUNDLE.search(page).group(1))

        assert len(bundle) > 500_000
        assert "mqtt" in bundle.lower()


class TestCaching:
    def test_the_page_and_the_sample_are_asked_for_again_every_time(self, host):
        fetch_until(host, "http://localhost/stats.json", '"at"')

        for path in ("", "?broker.host=localhost&broker.port=8080", "stats.json"):
            headers = host.check_output(HEADERS + shlex.quote("http://localhost/" + path))

            assert "Cache-Control: no-cache" in headers, (path, headers)

    def test_the_files_named_by_their_content_are_kept_for_good(self, host):
        page = fetch_until(host, "http://localhost/", "Netmon Web Display")

        for path in (BUNDLE.search(page).group(1), LOADING.search(page).group(1)):
            headers = host.check_output(HEADERS + "http://localhost/" + path)

            assert "Cache-Control: public, max-age=31536000, immutable" in headers, (path, headers)


class TestKiosk:
    def test_survives_a_web_server_restart(self, host):
        if not host.service("pihero-kiosk").is_running:
            pytest.skip("the kiosk runs only with a connected display")

        host.check_output("sudo systemctl restart lighttpd.service")
        time.sleep(3)

        assert host.service("pihero-kiosk").is_running


class TestStats:
    def test_the_sampler_runs_as_an_enabled_unit(self, host):
        unit = host.service("netmon-display-stats")

        assert unit.is_enabled
        assert unit.is_running

    def test_serves_the_latest_sample_as_json(self, host):
        text = fetch_until(host, "http://localhost/stats.json", '"at"')

        sample = json.loads(text)
        assert set(sample) == {"at", "interval", "kioskCpu", "webCpu", "kioskMemory"}
        assert sample["interval"] == 5
        if not host.service("pihero-kiosk").is_running:
            assert sample["kioskCpu"] is None
            assert sample["webCpu"] is None


class TestRemoval:
    @pytest.mark.mutating
    def test_purge_gives_lighttpd_its_root_back(self, host, target):
        target.purge(["netmon-display"])

        assert not host.file("/usr/share/netmon/web").exists
        assert not host.file("/etc/lighttpd/conf-enabled/90-netmon.conf").exists
        assert not host.file("/etc/lighttpd/conf-available/90-netmon.conf").exists
        assert not host.file("/usr/lib/systemd/system/netmon-display-stats.service").exists
        assert "Netmon" not in host.run(FETCH + "http://localhost/").stdout

        target.reinstall()


def fetch_until(host, url: str, needle: str, attempts: int = 15) -> str:
    out = ""
    for _ in range(attempts):
        out = host.run(FETCH + url).stdout
        if needle in out:
            return out
        time.sleep(1)
    return out
