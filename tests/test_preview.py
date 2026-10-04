import pytest
from pihero_testkit.preview import Settings
from pihero_testkit.preview.flavors import BoardServed, BrowserServed, VmServed

import preview
import vm_device

pytestmark = pytest.mark.tier0
SAMPLE = vm_device.SAMPLE.read_text()
KEY = "ssh-ed25519 AAAATEST pihero-testkit"


def settings(flavor: str, **environ: str) -> Settings:
    return Settings.from_environ(flavor, {**({"TARGET": "pi@netmon.local:2222"} if flavor == "board" else {}), **environ})


class TestRender:
    def test_leaves_out_netmons_packages(self):
        text = preview.render(SAMPLE, KEY)

        assert "  - pihero\n" in text
        assert "netmon-scanner" not in text
        assert "netmon-display" not in text

    def test_installs_the_kiosk_from_pi_heros_own_source(self):
        text = preview.render(SAMPLE, KEY)

        assert "  - pihero-kiosk\n" in text

    def test_leaves_out_netmons_apt_source(self):
        text = preview.render(SAMPLE, KEY)

        assert "netmon.sources" not in text
        assert "bkahlert.github.io/netmon" not in text
        assert "10.0.2.2:8000" not in text

    def test_leaves_out_the_boot_config_lines_of_netmons_packages(self):
        text = preview.render(SAMPLE, KEY)

        assert "bootconfig add cmdline" not in text

    def test_keeps_the_kiosk_settings_and_the_testkit_user(self):
        text = preview.render(SAMPLE, KEY)

        assert "COG_PLATFORM_DRM_VIDEO_MODE=800x480" in text
        assert "  - name: pihero\n" in text
        assert f"      - {KEY}\n" in text

    def test_changes_nothing_but_what_it_leaves_out(self):
        rendered = vm_device.render(SAMPLE, KEY).splitlines()

        kept = preview.render(SAMPLE, KEY).splitlines()

        assert [line for line in kept if line not in rendered] == ["  - pihero-kiosk"]
        assert len(rendered) - len(kept) == 9


class TestApp:
    def test_is_named_for_the_record_and_the_boards_run_directory(self):
        assert preview.Netmon.name == "netmon"
        assert preview.Netmon.root == preview.ROOT
        assert preview.Netmon.display == (800, 480)

    def test_gives_the_vm_the_rendered_sample(self):
        text = preview.Netmon().user_data()

        assert "  - pihero-kiosk\n" in text and "netmon.sources" not in text


class TestDevServer:
    def test_runs_gradles_continuous_development_server_on_8081(self):
        server = preview.Netmon().dev_server(settings("browser"))

        assert server.argv == ["./gradlew", "--console=plain", "jsBrowserDevelopmentRun", "--continuous"]
        assert server.port == 8081

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_proxies_no_stats_off_the_board(self, flavor):
        assert preview.Netmon().dev_server(settings(flavor)).env == {}

    def test_proxies_the_boards_stats_from_its_web_server_whatever_the_ssh_port(self):
        server = preview.Netmon().dev_server(settings("board"))

        assert server.env == {"NETMON_STATS_PROXY": "http://netmon.local"}


class TestHostOf:
    @pytest.mark.parametrize("target, host", [("pi@netmon.local", "netmon.local"), ("pi@netmon.local:2222", "netmon.local"), ("netmon.local", "netmon.local")])
    def test_is_the_host_of_user_at_host_and_port(self, target, host):
        assert preview.host_of(target) == host


class TestBackend:
    def test_is_the_fake_with_14_recent_and_39_stable_hosts_by_default(self):
        backend = preview.Netmon().backend(settings("vm"))

        assert backend.managed and backend.describe() == "fake on localhost:8080"
        assert (backend.scan.recent, backend.scan.stable) == (14, 39)

    def test_takes_the_broker_and_the_scan_from_the_environment(self):
        backend = preview.Netmon().backend(settings("vm", BROKER="netmon.local:8080", SCAN="2+3"))

        assert not backend.managed and backend.describe() == "netmon.local:8080"
        assert (backend.scan.recent, backend.scan.stable) == (2, 3)

    def test_offers_the_boards_own_broker_on_the_board(self):
        assert preview.Netmon().backend(settings("board", BROKER="board")).describe() == "the board's own, 127.0.0.1:8080 on the board"

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_refuses_the_boards_own_broker_elsewhere(self, flavor):
        with pytest.raises(ValueError, match="BROKER=board is only for preview-board"):
            preview.Netmon().backend(settings(flavor, BROKER="board"))

    def test_names_a_malformed_variable(self):
        with pytest.raises(ValueError, match="BROKER must be fake, board or HOST:PORT"):
            preview.Netmon().backend(settings("vm", BROKER="nope"))
        with pytest.raises(ValueError, match="SCAN must be"):
            preview.Netmon().backend(settings("vm", SCAN="nope"))


class TestPageUrl:
    @pytest.mark.parametrize("flavor, served, expected", [
        ("browser", BrowserServed(), "http://localhost:8081/?broker.host=localhost&broker.port=8080"),
        ("vm", VmServed(), "http://10.0.2.2:8081/?broker.host=10.0.2.2&broker.port=8080"),
        ("board", BoardServed(), "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080"),
    ])
    def test_reaches_the_dev_server_and_the_fake_on_the_mac_as_the_flavor_does(self, flavor, served, expected):
        app = preview.Netmon()

        assert app.page_url(app.backend(settings(flavor)), served) == expected

    def test_reaches_a_broker_on_the_mac_by_its_own_port(self):
        app = preview.Netmon()

        url = app.page_url(app.backend(settings("vm", BROKER="localhost:9000")), VmServed())

        assert url == "http://10.0.2.2:8081/?broker.host=10.0.2.2&broker.port=9000"

    def test_reaches_a_remote_broker_where_it_is(self):
        app = preview.Netmon()

        url = app.page_url(app.backend(settings("vm", BROKER="netmon.local:8080")), VmServed())

        assert url == "http://10.0.2.2:8081/?broker.host=netmon.local&broker.port=8080"

    def test_reaches_the_boards_own_broker_on_its_loopback(self):
        app = preview.Netmon()

        url = app.page_url(app.backend(settings("board", BROKER="board")), BoardServed())

        assert url == "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=8080"
