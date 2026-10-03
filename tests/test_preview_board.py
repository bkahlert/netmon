import subprocess
from types import SimpleNamespace

import pytest
from pihero_testkit import ssh

import preview_board
import preview_kiosk
from preview_broker import DEVICE, EXTERNAL, FIXTURE, Broker

pytestmark = pytest.mark.tier0
TARGET = "pi@netmon.local"
FIXTURE_BROKER = Broker(FIXTURE, "localhost", 8080)
DEVICE_BROKER = Broker(DEVICE, "127.0.0.1", 8080)
SAMPLE_CONF = """\
URL=http://localhost/?broker.host=localhost&broker.port=8080
COG_ARGS="--doc-viewer --web-mem-limit=200"
JSC_useJIT=false
"""


class TestHostOf:
    @pytest.mark.parametrize("target", ["pi@netmon.local", "pi@netmon.local:2222", "netmon.local"])
    def test_is_the_host_of_user_host_and_port(self, target):
        assert preview_board.host_of(target) == "netmon.local"


class TestStatsOrigin:
    def test_is_the_boards_web_server_whatever_the_ssh_port(self):
        assert preview_board.stats_origin("pi@netmon.local:2222") == "http://netmon.local"


class TestOnTheMac:
    @pytest.mark.parametrize("broker, expected", [
        (FIXTURE_BROKER, True),
        (Broker(EXTERNAL, "localhost", 9000), True),
        (Broker(EXTERNAL, "127.0.0.1", 9000), True),
        (Broker(EXTERNAL, "::1", 9000), True),
        (Broker(EXTERNAL, "netmon.local", 9000), False),
        (DEVICE_BROKER, False),
    ])
    def test_is_true_for_the_fixture_and_a_loopback_host_port(self, broker, expected):
        assert preview_board.on_the_mac(broker) is expected


class TestPageUrl:
    def test_reaches_a_broker_on_the_mac_through_the_reverse_port(self):
        url = preview_board.page_url(Broker(EXTERNAL, "localhost", 9000))

        assert url == "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080"

    def test_reaches_the_boards_own_broker_on_its_loopback(self):
        assert preview_board.page_url(DEVICE_BROKER) == "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=8080"

    def test_passes_a_remote_broker_unchanged(self):
        url = preview_board.page_url(Broker(EXTERNAL, "netmon.local", 9000))

        assert url == "http://127.0.0.1:18081/?broker.host=netmon.local&broker.port=9000"


class TestForwards:
    def test_forwards_the_dev_server_the_inspector_and_a_broker_on_the_mac(self):
        args = preview_board.forwards(Broker(EXTERNAL, "localhost", 9000), 8081, 2999)

        assert args == ["-R", "127.0.0.1:18081:127.0.0.1:8081", "-L", "127.0.0.1:2999:127.0.0.1:2999", "-R", "127.0.0.1:18080:127.0.0.1:9000"]

    @pytest.mark.parametrize("broker", [DEVICE_BROKER, Broker(EXTERNAL, "netmon.local", 9000)])
    def test_forwards_no_broker_that_is_not_on_the_mac(self, broker):
        args = preview_board.forwards(broker, 8081, 2999)

        assert args == ["-R", "127.0.0.1:18081:127.0.0.1:8081", "-L", "127.0.0.1:2999:127.0.0.1:2999"]


class TestTunnelCommand:
    def test_holds_the_forwards_open_without_a_command_and_fails_on_a_forward_that_cannot_be_made(self):
        command = preview_board.tunnel_command("pi@netmon.local:2222", ["-R", "a"])

        assert command == [
            "ssh", "-N", "-4", "-o", "BatchMode=yes", "-o", "ExitOnForwardFailure=yes", "-o", "ConnectTimeout=10",
            *ssh.KEEPALIVE, "-p", "2222", "-R", "a", "pi@netmon.local",
        ]

    def test_leaves_the_port_out_without_one(self):
        command = preview_board.tunnel_command(TARGET, [])

        assert "-p" not in command
        assert command[-1] == TARGET


class TestCheckKiosk:
    def test_passes_a_board_with_the_kiosk(self):
        preview_board.Board(TARGET, run=Script({})).check_kiosk()

    def test_names_an_unreachable_board(self):
        board = preview_board.Board(TARGET, run=Script({"dpkg-query": (255, "", "ssh: connect to host netmon.local port 22: Connection refused")}))

        with pytest.raises(RuntimeError, match="cannot reach pi@netmon.local over ssh: .*Connection refused"):
            board.check_kiosk()

    def test_names_a_board_without_the_kiosk(self):
        board = preview_board.Board(TARGET, run=Script({"dpkg-query": (1, "", "no packages found")}))

        with pytest.raises(RuntimeError, match="pi@netmon.local has no pihero-kiosk"):
            board.check_kiosk()


class TestSessionConf:
    def test_is_the_boards_kiosk_conf_pointed_at_the_tunnel_and_asks_nothing_to_be_written(self):
        script = Script(replies())

        conf = preview_board.Board(TARGET, run=script).session_conf(FIXTURE_BROKER)

        assert conf == preview_kiosk.session_kiosk_conf(SAMPLE_CONF, "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080", 2999)
        assert [stdin for _, stdin in script.calls if stdin] == []
        assert [remote for remote, _ in script.calls] == ["cat /etc/pihero/kiosk.conf"]

    def test_refuses_a_kiosk_conf_it_cannot_use(self):
        board = preview_board.Board(TARGET, run=Script(replies(conf="URL=http://localhost/\n")))

        with pytest.raises(ValueError, match="kiosk.conf"):
            board.session_conf(FIXTURE_BROKER)

    def test_names_a_kiosk_conf_it_cannot_read(self):
        board = preview_board.Board(TARGET, run=Script({"cat /etc/pihero/kiosk.conf": (1, "", "No such file")}))

        with pytest.raises(RuntimeError, match="cannot read /etc/pihero/kiosk.conf on pi@netmon.local: No such file"):
            board.session_conf(FIXTURE_BROKER)


class TestInstall:
    def test_writes_the_session_conf_and_the_drop_in_in_one_command_and_waits_for_the_page(self):
        script = Script(replies())
        board = preview_board.Board(TARGET, run=script)

        board.install("URL=x\n", ALIVE, sleep=lambda s: None)

        sent = [(remote, stdin) for remote, stdin in script.calls if stdin]
        assert [stdin for _, stdin in sent] == ["URL=x\n"]
        remote = sent[0][0]
        assert "/run/netmon-preview/kiosk.conf" in remote
        assert "/run/systemd/system/pihero-kiosk.service.d/preview.conf" in remote
        assert "EnvironmentFile=/run/netmon-preview/kiosk.conf" in remote
        assert "daemon-reload" in remote and "restart pihero-kiosk" in remote

    def test_reports_the_command_that_failed(self):
        board = preview_board.Board(TARGET, run=Script(replies(install=(1, "", "sudo: a password is required"))))

        with pytest.raises(RuntimeError, match="a password is required"):
            board.install("URL=x\n", ALIVE, sleep=lambda s: None)

    def test_gives_up_on_a_page_the_kiosk_never_loads(self):
        now = iter(range(0, 1000, 10))
        board = preview_board.Board(TARGET, run=Script(replies(loaded="0\n")))

        with pytest.raises(TimeoutError, match="did not load"):
            board.install("URL=x\n", ALIVE, timeout=30, sleep=lambda s: None, clock=lambda: next(now))

    def test_names_why_the_tunnel_ended_instead_of_waiting_for_a_page_that_cannot_come(self, tmp_path):
        log = tmp_path / "tunnel.log"
        log.write_text("Warning: remote port forwarding failed for listen port 18081\n")
        board = preview_board.Board(TARGET, run=Script(replies(loaded="0\n")), tunnel_log=log)

        with pytest.raises(RuntimeError, match="the ssh tunnel to pi@netmon.local ended: Warning: remote port forwarding failed for listen port 18081"):
            board.install("URL=x\n", SimpleNamespace(poll=lambda: 255), sleep=lambda s: None)


class TestTunnelProblem:
    def test_is_none_while_the_tunnel_runs(self):
        board = preview_board.Board(TARGET)

        assert board.tunnel_problem(SimpleNamespace(poll=lambda: None)) is None

    def test_names_why_an_ended_tunnel_ended(self, tmp_path):
        log = tmp_path / "tunnel.log"
        log.write_text("Timeout, server netmon.local not responding.\n")
        board = preview_board.Board(TARGET, tunnel_log=log)

        problem = board.tunnel_problem(SimpleNamespace(poll=lambda: 255))

        assert problem == "the ssh tunnel to pi@netmon.local ended: Timeout, server netmon.local not responding."


class TestTunnelEnded:
    def test_skips_the_per_connection_noise_for_the_reason(self, tmp_path):
        log = tmp_path / "tunnel.log"
        log.write_text("Timeout, server netmon.local not responding.\nchannel 3: open failed: connect failed: Connection refused\n")
        board = preview_board.Board(TARGET, tunnel_log=log)

        problem = board.tunnel_problem(SimpleNamespace(poll=lambda: 255))

        assert problem == "the ssh tunnel to pi@netmon.local ended: Timeout, server netmon.local not responding."

    def test_gives_the_exit_status_when_ssh_said_nothing_but_noise(self, tmp_path):
        log = tmp_path / "tunnel.log"
        log.write_text("channel 2: open failed: connect failed: Connection refused\n")
        board = preview_board.Board(TARGET, tunnel_log=log)

        problem = board.tunnel_problem(SimpleNamespace(poll=lambda: -15))

        assert problem == f"the ssh tunnel to pi@netmon.local ended with status -15 and no message; see {log}"


class TestOpenTunnel:
    def test_ends_the_board_side_of_a_dead_tunnel_before_it_asks_for_the_same_ports_again(self, monkeypatch, tmp_path):
        script = Script({})
        launched = fake_ssh(monkeypatch, status=None, message="", script=script)
        monkeypatch.setattr(preview_board.preview_process, "answers", lambda host, port: True)

        preview_board.Board(TARGET, run=script, tunnel_log=tmp_path / "tunnel.log").open_tunnel(FIXTURE_BROKER, 8081, 2999)

        remote = launched["calls_before_launch"][0][0]
        assert "127\\.0\\.0\\.1:(18081|18080) " in remote
        assert "grep sshd" in remote and "xargs -r sudo kill" in remote


    def test_sends_the_tunnels_messages_to_a_file_instead_of_a_pipe_nobody_reads(self, monkeypatch, tmp_path):
        log = tmp_path / "tunnel.log"
        launched = fake_ssh(monkeypatch, status=None, message="connect_to 127.0.0.1 port 8080: failed.\n")
        monkeypatch.setattr(preview_board.preview_process, "answers", lambda host, port: True)

        preview_board.Board(TARGET, run=Script({}), tunnel_log=log).open_tunnel(FIXTURE_BROKER, 8081, 2999)

        assert launched["stderr"] is not subprocess.PIPE
        assert log.read_text() == "connect_to 127.0.0.1 port 8080: failed.\n"

    def test_names_why_the_tunnel_ended_before_it_came_up(self, monkeypatch, tmp_path):
        log = tmp_path / "tunnel.log"
        fake_ssh(monkeypatch, status=255, message="pi@netmon.local: Permission denied (publickey).\n")

        with pytest.raises(RuntimeError, match=r"the ssh tunnel to pi@netmon.local ended: pi@netmon.local: Permission denied \(publickey\)."):
            preview_board.Board(TARGET, run=Script({}), tunnel_log=log).open_tunnel(FIXTURE_BROKER, 8081, 2999)


class TestRestore:
    def test_removes_the_session_files_and_restarts_the_kiosk(self):
        script = Script({})

        restored = preview_board.Board(TARGET, run=script).restore()

        remote = script.calls[0][0]
        assert restored is True
        assert "rm -rf /run/netmon-preview /run/systemd/system/pihero-kiosk.service.d/preview.conf" in remote
        assert "daemon-reload" in remote and "restart pihero-kiosk" in remote

    def test_warns_and_says_a_reboot_helps_on_a_board_that_does_not_answer(self, capsys):
        restored = preview_board.Board(TARGET, run=Script({"rm -rf": (255, "", "Connection timed out")})).restore()

        assert restored is False
        assert "a reboot of the board removes the session's files" in capsys.readouterr().err

    def test_warns_on_a_command_that_hangs(self, capsys):
        def hangs(argv, **kwargs):
            raise subprocess.TimeoutExpired(argv, 30)

        restored = preview_board.Board(TARGET, run=hangs).restore()

        assert restored is False
        assert "could not restore the kiosk on pi@netmon.local" in capsys.readouterr().err


ALIVE = SimpleNamespace(poll=lambda: None)


def fake_ssh(monkeypatch, status, message, script=None):
    launched = {}

    def popen(argv, **kwargs):
        launched["calls_before_launch"] = list(script.calls) if script else []
        kwargs["stderr"].write(message)
        kwargs["stderr"].flush()
        launched.update(kwargs)
        return SimpleNamespace(poll=lambda: status, pid=1)

    monkeypatch.setattr(preview_board.subprocess, "Popen", popen)
    return launched


class Script:
    def __init__(self, replies):
        self.replies, self.calls = replies, []

    def __call__(self, argv, input=None, **kwargs):
        remote = argv[-1]
        self.calls.append((remote, input))
        for needle, (code, out, err) in self.replies.items():
            if needle in remote:
                return subprocess.CompletedProcess(argv, code, out, err)
        return subprocess.CompletedProcess(argv, 0, "", "")


def replies(conf=SAMPLE_CONF, install=(0, "", ""), loaded="1\n"):
    return {
        "cat /etc/pihero/kiosk.conf": (0, conf, ""),
        "date '+": (0, "2026-10-03 10:00:00\n", ""),
        "tee /run/netmon-preview/kiosk.conf": install,
        "grep -c": (0, loaded, ""),
    }
