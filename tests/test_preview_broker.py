import socket
import subprocess

import pytest

import preview_broker
from preview_broker import DEVICE, EXTERNAL, FIXTURE, Broker
from scan_fixtures import Scan


@pytest.mark.tier0
class TestParseBroker:
    @pytest.mark.parametrize("text", [None, "", "fixture"])
    def test_is_the_fixture_on_the_macs_8080_by_default(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker == Broker(FIXTURE, "localhost", 8080)

    def test_is_the_boards_own_broker_on_its_loopback_for_device(self):
        broker = preview_broker.parse_broker("device")

        assert broker == Broker(DEVICE, "127.0.0.1", 8080)

    @pytest.mark.parametrize("text", ["localhost:8080", "127.0.0.1:8080", "localhost:9000", "netmon.local:8080"])
    def test_uses_a_host_and_port_as_it_is(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker.kind == EXTERNAL
        assert broker.managed is False
        assert broker.address == text

    @pytest.mark.parametrize("text", ["netmon.local", ":8080", "host:", "host:abc", "host:0", "host:70000", "host:-1", "Fixture", "mock"])
    def test_rejects_anything_but_the_three_forms(self, text):
        with pytest.raises(ValueError, match="BROKER must be fixture, device or HOST:PORT"):
            preview_broker.parse_broker(text)


@pytest.mark.tier0
class TestBroker:
    @pytest.mark.parametrize("text, described", [("fixture", "fixture on localhost:8080"), ("device", "the device's own, 127.0.0.1:8080 on the board"), ("netmon.local:8080", "netmon.local:8080")])
    def test_describes_itself_for_the_ready_message(self, text, described):
        assert preview_broker.parse_broker(text).describe() == described


@pytest.mark.tier0
class TestEnsure:
    def test_refuses_a_port_that_already_answers_and_names_the_way_to_use_it(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen()
            port = server.getsockname()[1]

            with pytest.raises(RuntimeError, match=f"port {port} is taken; to use the broker there, run with BROKER=localhost:{port}"):
                preview_broker.ensure(Broker(FIXTURE, "127.0.0.1", port), Scan(1, 1, 1))


@pytest.mark.tier0
class TestCommands:
    def test_run_publishes_the_websocket_listener_on_the_loopback_only(self):
        command = preview_broker.run_command(Broker(FIXTURE, "localhost", 8080))

        assert command[command.index("--publish") + 1] == "127.0.0.1:8080:8080"
        assert command[-1] == "docker.io/library/eclipse-mosquitto:2"

    def test_run_mounts_the_boards_configuration_read_only(self):
        command = preview_broker.run_command(Broker(FIXTURE, "localhost", 8080))

        volume = command[command.index("--volume") + 1]
        assert volume.endswith("packages/netmon-scanner/conf/mosquitto-netmon.conf:/mosquitto/config/mosquitto.conf:ro")

    def test_publish_retains_the_scan_as_json_under_its_topic(self):
        command = preview_broker.publish_command("dt/netmon/x/scan", {"event": "scan"})

        assert command[:3] == ["podman", "exec", "netmon-preview-broker"]
        assert command[command.index("-t") + 1] == "dt/netmon/x/scan"
        assert command[command.index("-m") + 1] == '{"event": "scan"}'
        assert "-r" in command


@pytest.mark.tier0
class TestMain:
    def test_refuses_a_broker_it_does_not_manage(self, capsys):
        status = preview_broker.main({"BROKER": "netmon.local:8080"})

        assert status == 2
        assert "netmon.local:8080" in capsys.readouterr().err

    @pytest.mark.parametrize("environ, message", [({"BROKER": "nope"}, "BROKER must be"), ({"SCAN": "nope"}, "SCAN must be")])
    def test_names_a_malformed_variable(self, capsys, environ, message):
        status = preview_broker.main(environ)

        assert status == 2
        assert message in capsys.readouterr().err


@pytest.mark.preview
class TestBrokerContainer:
    def test_serves_the_fixture_as_retained_messages_over_websockets(self):
        broker = Broker(FIXTURE, "127.0.0.1", free_port())

        preview_broker.ensure(broker, Scan(2, 1, 2))
        try:
            retained = subprocess.run(
                ["podman", "exec", preview_broker.CONTAINER, "mosquitto_sub", "-h", "127.0.0.1", "-p", "1883", "-t", "dt/netmon/#", "-C", "2", "-W", "10", "-v"],
                capture_output=True, text=True, check=False,
            )
            handshake = websocket_handshake(broker)
        finally:
            preview_broker.stop()

        assert len(retained.stdout.splitlines()) == 2
        assert handshake.startswith("HTTP/1.1 101")

    def test_refuses_a_broker_that_already_answers(self):
        broker = Broker(FIXTURE, "127.0.0.1", free_port())
        preview_broker.ensure(broker, Scan(1, 1, 1))
        try:
            with pytest.raises(RuntimeError, match="is taken"):
                preview_broker.ensure(broker, Scan(1, 1, 1))
        finally:
            preview_broker.stop()


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def websocket_handshake(broker: Broker) -> str:
    request = (
        "GET / HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: mqtt\r\n\r\n"
    )
    with socket.create_connection((broker.host, broker.port), timeout=5) as s:
        s.sendall(request.encode())
        return s.recv(200).decode(errors="replace")
