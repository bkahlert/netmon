import socket
import subprocess

import pytest

import preview_broker
from preview_broker import BOARD, EXTERNAL, FAKE, Broker, MosquittoBackend
from scan_fixtures import Scan


@pytest.mark.tier0
class TestParseBroker:
    @pytest.mark.parametrize("text", [None, "", "fake", "fixture"])
    def test_is_the_fake_on_the_macs_8080_by_default(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker == Broker(FAKE, "localhost", 8080)

    @pytest.mark.parametrize("text", ["board", "device"])
    def test_is_the_boards_own_broker_on_its_loopback_for_board(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker == Broker(BOARD, "127.0.0.1", 8080)

    @pytest.mark.parametrize("text", ["localhost:8080", "127.0.0.1:8080", "localhost:9000", "netmon.local:8080"])
    def test_uses_a_host_and_port_as_it_is(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker.kind == EXTERNAL
        assert broker.managed is False
        assert broker.address == text

    @pytest.mark.parametrize("text", ["netmon.local", ":8080", "host:", "host:abc", "host:0", "host:70000", "host:-1", "Fake", "mock"])
    def test_rejects_anything_but_the_three_forms(self, text):
        with pytest.raises(ValueError, match="BROKER must be fake, board or HOST:PORT"):
            preview_broker.parse_broker(text)


@pytest.mark.tier0
class TestBroker:
    @pytest.mark.parametrize("text, described", [("fake", "fake on localhost:8080"), ("board", "the board's own, 127.0.0.1:8080 on the board"), ("netmon.local:8080", "netmon.local:8080")])
    def test_describes_itself_for_the_ready_message(self, text, described):
        assert preview_broker.parse_broker(text).describe() == described


@pytest.mark.tier0
class TestMosquittoBackend:
    @pytest.mark.parametrize("text, managed, mac_port", [
        ("fake", True, 8080),
        ("localhost:9000", False, 9000),
        ("127.0.0.1:9000", False, 9000),
        ("::1:9000", False, 9000),
        ("netmon.local:8080", False, None),
        ("board", False, None),
    ])
    def test_knows_what_it_starts_and_which_mac_port_the_kiosk_must_reach(self, text, managed, mac_port):
        backend = MosquittoBackend(preview_broker.parse_broker(text), Scan(1, 1, 1))

        assert (backend.managed, backend.mac_port) == (managed, mac_port)

    @pytest.mark.parametrize("text, address", [
        ("fake", ("10.0.2.2", 8080)),
        ("localhost:9000", ("10.0.2.2", 9000)),
        ("netmon.local:8080", ("netmon.local", 8080)),
        ("board", ("127.0.0.1", 8080)),
    ])
    def test_gives_the_broker_as_the_page_reaches_it(self, text, address):
        backend = MosquittoBackend(preview_broker.parse_broker(text), Scan(1, 1, 1))

        assert backend.address_for(Served("10.0.2.2")) == address

    def test_describes_its_broker(self):
        assert MosquittoBackend(preview_broker.parse_broker("fake"), Scan(1, 1, 1)).describe() == "fake on localhost:8080"

    def test_stops_without_ever_having_run(self):
        MosquittoBackend(preview_broker.parse_broker("netmon.local:8080"), Scan(1, 1, 1)).stop()


@pytest.mark.tier0
class TestEnsure:
    def test_refuses_a_port_that_already_answers_and_names_the_way_to_use_it(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen()
            port = server.getsockname()[1]

            with pytest.raises(RuntimeError, match=f"port {port} is taken; to use the broker there, run with BROKER=localhost:{port}"):
                preview_broker.ensure(Broker(FAKE, "127.0.0.1", port), Scan(1, 1, 1))


@pytest.mark.tier0
class TestStart:
    def test_refuses_a_port_that_already_answers(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen()
            port = server.getsockname()[1]

            with pytest.raises(RuntimeError, match=f"port {port} is taken"):
                preview_broker.start(Broker(FAKE, "127.0.0.1", port))

    def test_names_no_preview_variable_for_a_taken_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen()
            port = server.getsockname()[1]

            with pytest.raises(RuntimeError) as raised:
                preview_broker.start(Broker(FAKE, "127.0.0.1", port))

        assert "BROKER" not in str(raised.value)


@pytest.mark.tier0
class TestCommands:
    def test_run_publishes_the_websocket_listener_on_the_loopback_only(self):
        command = preview_broker.run_command(Broker(FAKE, "localhost", 8080))

        assert command[command.index("--publish") + 1] == "127.0.0.1:8080:8080"
        assert command[-1] == "docker.io/library/eclipse-mosquitto:2"

    def test_run_mounts_the_boards_configuration_read_only(self):
        command = preview_broker.run_command(Broker(FAKE, "localhost", 8080))

        volume = command[command.index("--volume") + 1]
        assert volume.endswith("packages/netmon-scanner/conf/mosquitto-netmon.conf:/mosquitto/config/mosquitto.conf:ro")

    def test_publish_retains_the_scan_as_json_under_its_topic(self):
        command = preview_broker.publish_command("dt/netmon/x/scan", {"event": "scan"})

        assert command[:3] == ["podman", "exec", "netmon-preview-broker"]
        assert command[command.index("-t") + 1] == "dt/netmon/x/scan"
        assert command[command.index("-m") + 1] == '{"event": "scan"}'
        assert "-r" in command

    def test_clear_sends_an_empty_retained_message(self):
        command = preview_broker.clear_command("dt/netmon/x/scan")

        assert command[:3] == ["podman", "exec", "netmon-preview-broker"]
        assert command[command.index("-t") + 1] == "dt/netmon/x/scan"
        assert "-r" in command
        assert "-n" in command
        assert "-m" not in command


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
        broker = Broker(FAKE, "127.0.0.1", free_port())

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
        broker = Broker(FAKE, "127.0.0.1", free_port())
        preview_broker.ensure(broker, Scan(1, 1, 1))
        try:
            with pytest.raises(RuntimeError, match="is taken"):
                preview_broker.ensure(broker, Scan(1, 1, 1))
        finally:
            preview_broker.stop()

    def test_starts_empty_and_clears_a_retained_scan(self):
        broker = Broker(FAKE, "127.0.0.1", free_port())

        preview_broker.start(broker)
        try:
            empty = retained_messages()
            preview_broker.publish({"dt/netmon/x/scan": {"event": "scan"}})
            published = retained_messages()
            preview_broker.clear("dt/netmon/x/scan")
            cleared = retained_messages()
        finally:
            preview_broker.stop()

        assert (empty, published, cleared) == ("", 'dt/netmon/x/scan {"event": "scan"}\n', "")


class Served:
    """Reaches the Mac's ports at `host`, as the VM's kiosk does."""

    flavor = "vm"

    def __init__(self, host: str):
        self.host = host

    def address(self, mac_port: int) -> str:
        return f"{self.host}:{mac_port}"


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


def retained_messages() -> str:
    return subprocess.run(
        ["podman", "exec", preview_broker.CONTAINER, "mosquitto_sub", "-h", "127.0.0.1", "-p", "1883", "-t", "dt/netmon/#", "-C", "1", "-W", "2", "-v"],
        capture_output=True, text=True, check=False,
    ).stdout
