import time

import pytest

pytestmark = pytest.mark.installed


class TestPackage:
    def test_is_installed_at_the_built_version(self, host, version):
        package = host.package("netmon-scanner")

        assert package.is_installed
        assert package.version == version

    def test_pulls_in_the_runtime(self, host):
        for name in ("nmap", "mosquitto", "default-jre-headless"):
            assert host.package(name).is_installed, name

    def test_leaves_nmaps_binary_as_debian_ships_it(self, host, libcap):
        assert host.check_output("getcap /usr/bin/nmap").strip() == ""


class TestUser:
    def test_netmon_is_a_system_user_without_a_login(self, host):
        user = host.user("netmon")

        assert user.exists
        assert user.uid < 1000
        assert user.shell in ("/usr/sbin/nologin", "/bin/false")


class TestBroker:
    def test_listens_for_websockets_on_8080_and_locally_on_1883(self, host):
        listening = host.check_output("ss -Hltn")

        assert ":8080 " in listening
        assert "127.0.0.1:1883 " in listening


class TestUnit:
    def test_is_enabled(self, host):
        assert host.service("netmon-scanner").is_enabled

    def test_carries_the_capabilities_and_the_memory_cap(self, host):
        show = host.check_output("systemctl show -p User -p AmbientCapabilities -p MemoryMax netmon-scanner.service")

        assert "User=netmon" in show
        assert "AmbientCapabilities=cap_net_admin cap_net_raw" in show
        assert "MemoryMax=335544320" in show

    def test_starts_the_jvm_with_the_shipped_options(self, host):
        """The unit's JAVA_TOOL_OPTIONS reach the JVM whole; unquoted, systemd dropped everything after the first space."""
        log = journal_until(host, "Picked up JAVA_TOOL_OPTIONS")
        assert "Picked up JAVA_TOOL_OPTIONS: -Xmx128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1" in log

    def test_connects_to_the_broker(self, host):
        log = journal_until(host, "connected")

        assert "connected" in log
        assert host.service("netmon-scanner").is_running

    def test_completes_and_publishes_a_scan(self, host, request):
        if request.config.getoption("--target") == "podman":
            pytest.skip("a scan of the container's /16 takes minutes; proven in the VM and on a device")

        log = journal_until(host, "completed and published to", attempts=90)

        assert "completed and published to" in log
        # The scanner falls back to an unprivileged scan (no MAC addresses, no vendors) when nmap lacks raw sockets.
        assert "Switching to unprivileged mode" not in log

    def test_the_scan_is_retained_at_the_broker(self, host, request, mosquitto_clients):
        if request.config.getoption("--target") == "podman":
            pytest.skip("a scan of the container's /16 takes minutes; proven in the VM and on a device")

        out = host.check_output("mosquitto_sub -h 127.0.0.1 -t 'dt/netmon/+/+/+/+/scan' -C 1 -W 120")

        assert '"event":"scan"' in out.replace(" ", "")


class TestStop:
    @pytest.mark.mutating
    def test_a_stop_leaves_the_unit_inactive_not_failed(self, host):
        host.check_output("sudo systemctl stop netmon-scanner.service")
        state = host.check_output("systemctl show -p ActiveState --value netmon-scanner.service").strip()
        result = host.check_output("systemctl show -p Result --value netmon-scanner.service").strip()
        host.check_output("sudo systemctl start netmon-scanner.service")

        assert (state, result) == ("inactive", "success")


class TestRemoval:
    @pytest.mark.mutating
    def test_purge_leaves_nothing_behind(self, host, target):
        target.purge(["netmon-scanner"])

        assert not host.file("/usr/share/netmon/netmon-scanner.jar").exists
        assert not host.file("/etc/mosquitto/conf.d/netmon.conf").exists
        assert not host.file("/var/lib/netmon").exists
        assert not host.user("netmon").exists

        target.reinstall()


@pytest.fixture(scope="module")
def libcap(request, target):
    if target.host.exists("getcap"):
        return
    if request.config.getoption("--target") == "ssh":
        pytest.skip("libcap2-bin is not installed on the device")
    target.install_extra(["libcap2-bin"])


@pytest.fixture(scope="module")
def mosquitto_clients(request, target):
    if target.host.exists("mosquitto_sub"):
        return
    if request.config.getoption("--target") == "ssh":
        pytest.skip("mosquitto-clients is not installed on the device")
    target.install_extra(["mosquitto-clients"])


def journal_until(host, needle: str, attempts: int = 45) -> str:
    log = ""
    for _ in range(attempts):
        log = host.run("journalctl -u netmon-scanner -b --no-pager -o cat").stdout
        if needle in log:
            return log
        time.sleep(2)
    return log
