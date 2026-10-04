import json
import time

import pytest

pytestmark = pytest.mark.installed
TOPIC = "dt/netmon/+/metrics"


class TestPackage:
    def test_is_installed_at_the_built_version(self, host, version):
        package = host.package("netmon-metrics")

        assert package.is_installed
        assert package.version == version

    def test_pulls_in_the_broker_and_the_bus(self, host):
        for name in ("mosquitto", "dbus"):
            assert host.package(name).is_installed, name


class TestUser:
    def test_netmon_metrics_is_a_system_user_without_a_login(self, host):
        user = host.user("netmon-metrics")

        assert user.exists
        assert user.uid < 1000
        assert user.shell in ("/usr/sbin/nologin", "/bin/false")


class TestUnit:
    def test_is_enabled_and_running(self, host):
        unit = host.service("netmon-metrics")

        assert unit.is_enabled
        assert unit.is_running

    def test_runs_as_its_system_user_under_the_memory_cap(self, host):
        show = host.check_output("systemctl show -p User -p MemoryMax netmon-metrics.service")

        assert "User=netmon-metrics" in show
        assert "MemoryMax=25165824" in show


class TestMessage:
    def test_is_otlp_json_with_the_host(self, host, mosquitto_clients):
        request = latest(host, lambda r: True)

        resource = by_attribute(request, "service.name", "netmon-metrics")
        faults = metric(resource, "system.paging.faults")
        assert faults["sum"]["aggregationTemporality"] == 2
        assert isinstance(faults["sum"]["dataPoints"][0]["timeUnixNano"], str)
        limit = metric(resource, "system.memory.limit")
        assert limit["sum"]["aggregationTemporality"] == 2
        assert "isMonotonic" not in limit["sum"]
        assert resource["schemaUrl"] == "https://opentelemetry.io/schemas/1.43.0"

    def test_holds_the_scanner_unit_with_its_state_from_systemd(self, host, mosquitto_clients):
        request = latest(host, lambda r: unit(r, "netmon-scanner.service") is not None)

        scanner = unit(request, "netmon-scanner.service")
        states = {p["attributes"][0]["value"]["stringValue"]: p["asInt"] for p in metric(scanner, "systemd.unit.state")["gauge"]["dataPoints"]}
        assert states["active"] == "1"
        assert "systemd.unit.memory.usage" in names(scanner)

    def test_holds_the_scanner_process(self, host, mosquitto_clients):
        request = latest(host, lambda r: by_attribute(r, "process.executable.name", "netmon-scanner") is not None)

        assert "process.memory.usage" in names(by_attribute(request, "process.executable.name", "netmon-scanner"))


class TestKiosk:
    def test_holds_the_kiosk_and_its_web_process_with_utilization(self, host, mosquitto_clients):
        if not host.file("/dev/dri").exists:
            pytest.skip("the kiosk runs only with a display adapter")

        request = latest(host, lambda r: (w := by_attribute(r, "process.executable.name", "WPEWebProcess")) is not None and "process.cpu.utilization" in names(w))

        assert "systemd.unit.cpu.utilization" in names(unit(request, "pihero-kiosk.service"))


class TestBrokerRestart:
    @pytest.mark.mutating
    def test_publishes_again_after_the_broker_restarted(self, host, mosquitto_clients):
        host.check_output("sudo systemctl restart mosquitto.service")
        restarted = time.time()

        request = latest(host, lambda r: at(r) > restarted)

        assert at(request) - restarted < 20


class TestStop:
    @pytest.mark.mutating
    def test_a_stop_clears_the_topic(self, host, mosquitto_clients):
        host.check_output("sudo systemctl stop netmon-metrics.service")
        result = host.run(f"mosquitto_sub -h 127.0.0.1 -t '{TOPIC}' -C 1 -W 3")
        host.check_output("sudo systemctl start netmon-metrics.service")

        assert result.stdout.strip() == ""


class TestLastWill:
    @pytest.mark.mutating
    def test_a_killed_sampler_leaves_the_topic_empty(self, host, mosquitto_clients):
        host.check_output("sudo systemd-run --unit=netmon-metrics-will --property=User=netmon-metrics /usr/lib/netmon/netmon-metrics --node will")
        topic = "dt/netmon/will/metrics"
        assert host.run(f"mosquitto_sub -h 127.0.0.1 -t {topic} -C 1 -W 10").stdout.strip() != ""
        host.check_output("sudo systemctl kill --signal=SIGKILL netmon-metrics-will.service")
        time.sleep(1)

        result = host.run(f"mosquitto_sub -h 127.0.0.1 -t {topic} -C 1 -W 3")

        host.run("sudo systemctl reset-failed netmon-metrics-will.service")
        assert result.stdout.strip() == ""


class TestRemoval:
    @pytest.mark.mutating
    def test_purge_leaves_nothing_behind(self, host, target):
        target.purge(["netmon-metrics"])

        assert not host.file("/usr/lib/netmon/netmon-metrics").exists
        assert not host.file("/usr/lib/systemd/system/netmon-metrics.service").exists
        assert not host.user("netmon-metrics").exists

        target.reinstall()


@pytest.fixture(scope="module")
def mosquitto_clients(request, target):
    if target.host.exists("mosquitto_sub"):
        return
    if request.config.getoption("--target") == "ssh":
        pytest.skip("mosquitto-clients is not installed on the device")
    target.install_extra(["mosquitto-clients"])


def latest(host, ready, attempts: int = 12) -> dict:
    request = {}
    for _ in range(attempts):
        out = host.run(f"mosquitto_sub -h 127.0.0.1 -t '{TOPIC}' -C 1 -W 10").stdout.strip()
        if out:
            request = json.loads(out)
            if ready(request):
                return request
        time.sleep(5)
    return request


def by_attribute(request: dict, key: str, value: str) -> dict | None:
    for resource in request.get("resourceMetrics", []):
        if attributes(resource).get(key) == value:
            return resource
    return None


def unit(request: dict, name: str) -> dict | None:
    for resource in request.get("resourceMetrics", []):
        found = attributes(resource)
        if found.get("systemd.unit.name") == name and "process.pid" not in found:
            return resource
    return None


def attributes(resource: dict) -> dict[str, str]:
    return {a["key"]: next(iter(a["value"].values())) for a in resource["resource"]["attributes"]}


def names(resource: dict) -> set[str]:
    return {m["name"] for scope in resource["scopeMetrics"] for m in scope.get("metrics", [])}


def metric(resource: dict, name: str) -> dict:
    return next(m for scope in resource["scopeMetrics"] for m in scope.get("metrics", []) if m["name"] == name)


def at(request: dict) -> float:
    host = by_attribute(request, "service.name", "netmon-metrics")
    return max((int(p["timeUnixNano"]) for scope in host["scopeMetrics"] for m in scope.get("metrics", []) for p in (m.get("gauge") or m.get("sum"))["dataPoints"]), default=0) / 1e9
