import socket
import subprocess

import pytest

import preview_broker
from preview_broker import Broker
from scan_fixtures import Scan


@pytest.mark.tier0
class TestParseBroker:
    def test_manages_only_the_default(self):
        broker = preview_broker.parse_broker("localhost:8080")

        assert broker == Broker("localhost", 8080, managed=True)

    @pytest.mark.parametrize("text", ["127.0.0.1:8080", "localhost:9000", "netmon.local:8080"])
    def test_leaves_any_other_broker_alone(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker.managed is False

    @pytest.mark.parametrize("text", ["", "netmon.local", ":8080", "host:", "host:abc", "host:0", "host:70000", "host:-1"])
    def test_rejects_anything_but_host_and_port(self, text):
        with pytest.raises(ValueError, match="BROKER must be"):
            preview_broker.parse_broker(text)


@pytest.mark.tier0
class TestCommands:
    def test_run_publishes_the_websocket_listener_on_the_loopback_only(self):
        command = preview_broker.run_command(Broker("localhost", 8080, managed=True))

        assert command[command.index("--publish") + 1] == "127.0.0.1:8080:8080"
        assert command[-1] == "docker.io/library/eclipse-mosquitto:2"

    def test_run_mounts_the_boards_configuration_read_only(self):
        command = preview_broker.run_command(Broker("localhost", 8080, managed=True))

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
        broker = Broker("127.0.0.1", free_port(), managed=True)

        started = preview_broker.ensure(broker, Scan(2, 1, 2))
        try:
            retained = subprocess.run(
                ["podman", "exec", preview_broker.CONTAINER, "mosquitto_sub", "-h", "127.0.0.1", "-p", "1883", "-t", "dt/netmon/#", "-C", "2", "-W", "10", "-v"],
                capture_output=True, text=True, check=False,
            )
            handshake = websocket_handshake(broker)
        finally:
            preview_broker.stop()

        assert started is True
        assert len(retained.stdout.splitlines()) == 2
        assert handshake.startswith("HTTP/1.1 101")

    def test_leaves_a_broker_that_already_answers_alone(self):
        broker = Broker("127.0.0.1", free_port(), managed=True)
        assert preview_broker.ensure(broker, Scan(1, 1, 1)) is True
        try:
            started_again = preview_broker.ensure(broker, Scan(1, 1, 1))
        finally:
            preview_broker.stop()

        assert started_again is False


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
