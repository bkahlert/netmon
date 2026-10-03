import os
import socket
import subprocess
import time

import pytest

import preview_process

pytestmark = pytest.mark.tier0


class TestAnswers:
    def test_is_true_on_a_listening_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen()

            found = preview_process.answers("127.0.0.1", server.getsockname()[1])

        assert found is True

    def test_is_false_on_a_port_nobody_listens_on(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            port = server.getsockname()[1]

        assert preview_process.answers("127.0.0.1", port) is False


class TestCommandOf:
    def test_names_the_command_of_a_running_process(self):
        command = preview_process.command_of(os.getpid())

        assert "python" in command.lower() or "pytest" in command.lower()

    def test_is_none_for_a_process_that_is_gone(self):
        assert preview_process.command_of(2**22 + 12345) is None

    def test_is_none_for_a_process_that_ended_but_was_not_reaped(self):
        child = subprocess.Popen(["true"])
        try:
            time.sleep(0.5)

            command = preview_process.command_of(child.pid)
        finally:
            child.wait()

        assert command is None


class TestUntilInterrupted:
    def test_ends_with_the_problem_the_watch_reports(self):
        reports = iter([None, None, "the ssh tunnel to pi@netmon.local ended: Timeout"])

        with pytest.raises(RuntimeError, match="the ssh tunnel to pi@netmon.local ended: Timeout"):
            preview_process.until_interrupted(lambda: next(reports), sleep=lambda s: None)

    def test_ends_quietly_on_ctrl_c_while_it_waits(self):
        def interrupted(seconds):
            raise KeyboardInterrupt

        preview_process.until_interrupted(lambda: None, sleep=interrupted)
