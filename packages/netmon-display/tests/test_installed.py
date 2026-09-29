import time

import pytest

pytestmark = pytest.mark.installed
FETCH = "python3 -c 'import urllib.request,sys; print(urllib.request.urlopen(sys.argv[1], timeout=5).read().decode())' "


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
        assert 'src="netmon.js"' in page

    def test_the_bundle_is_served(self, host):
        bundle = host.check_output(FETCH + "http://localhost/netmon.js")

        assert len(bundle) > 1_000_000
        assert "mqtt" in bundle.lower()


class TestKiosk:
    def test_survives_a_web_server_restart(self, host):
        if not host.service("pihero-kiosk").is_running:
            pytest.skip("the kiosk runs only with a connected display")

        host.check_output("sudo systemctl restart lighttpd.service")
        time.sleep(3)

        assert host.service("pihero-kiosk").is_running


class TestRemoval:
    @pytest.mark.mutating
    def test_purge_gives_lighttpd_its_root_back(self, host, target):
        target.purge(["netmon-display"])

        assert not host.file("/usr/share/netmon/web").exists
        assert not host.file("/etc/lighttpd/conf-enabled/90-netmon.conf").exists
        assert not host.file("/etc/lighttpd/conf-available/90-netmon.conf").exists
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
