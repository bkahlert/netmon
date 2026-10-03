import pytest

import preview_kiosk

pytestmark = pytest.mark.tier0
SAMPLE_CONF = """\
URL=http://localhost/?broker.host=localhost&broker.port=8080
COG_PLATFORM_DRM_VIDEO_MODE=800x480
COG_ARGS="--doc-viewer --web-mem-limit=200 --web-check-interval=10 --webprocess-failure=restart"
JSC_useJIT=false
WEBKIT_SKIA_CPU_PAINTING_THREADS=1
"""
LISTING = """<html><head><title>Remote inspector</title></head><body><h1>Inspectable targets</h1><div id='targetlist'><table><tbody><tr><td class="data"><div class="targetname">Netmon Web Display</div><div class="targeturl">http://localhost/?broker.host=localhost&broker.port=8080</div></td><td class="input"><input type="button" value="Inspect" onclick="window.open('Main.html?ws=' + window.location.host + '/socket/1/1/WebPage', '_blank', 'location=no,menubar=no,status=no,toolbar=no');"></td></tr></tbody></table></div></body></html>"""
EMPTY_LISTING = "<html><body><h1>Inspectable targets</h1><div id='targetlist'><table><tbody></tbody></table></div></body></html>"


class TestGuestHost:
    @pytest.mark.parametrize("host", ["localhost", "127.0.0.1", "::1"])
    def test_is_the_macs_address_for_a_loopback_host(self, host):
        assert preview_kiosk.guest_host(host) == "10.0.2.2"

    def test_keeps_any_other_host(self):
        assert preview_kiosk.guest_host("netmon.local") == "netmon.local"


class TestPageUrl:
    def test_names_the_dev_server_and_the_broker_as_the_guest_reaches_them(self):
        url = preview_kiosk.page_url(8081, "localhost", 8080)

        assert url == "http://10.0.2.2:8081/?broker.host=10.0.2.2&broker.port=8080"

    def test_passes_a_remote_broker_unchanged(self):
        url = preview_kiosk.page_url(8081, "netmon.local", 8080)

        assert url == "http://10.0.2.2:8081/?broker.host=netmon.local&broker.port=8080"


class TestSessionKioskConf:
    def test_points_the_kiosk_at_the_url_and_turns_the_inspector_on(self):
        conf = preview_kiosk.session_kiosk_conf(SAMPLE_CONF, "http://10.0.2.2:8081/", 2999)

        assert conf == """\
URL=http://10.0.2.2:8081/
COG_PLATFORM_DRM_VIDEO_MODE=800x480
COG_ARGS="--enable-developer-extras=true --doc-viewer --web-mem-limit=200 --web-check-interval=10 --webprocess-failure=restart"
JSC_useJIT=false
WEBKIT_SKIA_CPU_PAINTING_THREADS=1
WEBKIT_INSPECTOR_HTTP_SERVER=127.0.0.1:2999
GSETTINGS_BACKEND=memory
"""

    @pytest.mark.parametrize("current", ["COG_ARGS=\"--doc-viewer\"\n", "URL=http://localhost/\n", ""])
    def test_refuses_a_file_it_cannot_change(self, current):
        with pytest.raises(ValueError, match="kiosk.conf"):
            preview_kiosk.session_kiosk_conf(current, "http://10.0.2.2:8081/", 2999)


class TestInspectorUrl:
    def test_opens_the_first_target_through_the_given_address(self):
        url = preview_kiosk.inspector_url(LISTING, "127.0.0.1:2999")

        assert url == "http://127.0.0.1:2999/Main.html?ws=127.0.0.1:2999/socket/1/1/WebPage"

    def test_is_none_on_a_listing_without_a_target(self):
        url = preview_kiosk.inspector_url(EMPTY_LISTING, "127.0.0.1:2999")

        assert url is None


class TestInspectApp:
    @pytest.mark.parametrize("value", ["", "0"])
    def test_is_none_on_a_value_that_opens_nothing(self, value):
        assert preview_kiosk.inspect_app(value) is None

    def test_is_the_application_name_otherwise(self):
        assert preview_kiosk.inspect_app("Google Chrome") == "Google Chrome"


class TestOpenCommand:
    def test_opens_the_url_in_the_application(self):
        command = preview_kiosk.open_command("Safari", "http://127.0.0.1:2999/")

        assert command == ["open", "-a", "Safari", "http://127.0.0.1:2999/"]
