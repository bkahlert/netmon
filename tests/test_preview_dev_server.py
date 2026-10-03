import re
from types import SimpleNamespace

import pytest

import preview_dev_server

pytestmark = pytest.mark.tier0


class TestWaitUntilServing:
    def test_returns_once_the_port_answers(self):
        process = SimpleNamespace(poll=lambda: None, returncode=None)

        preview_dev_server.wait_until_serving(process, answers=lambda host, port: True, sleep=lambda s: None)

    def test_names_the_log_on_a_gradle_that_exited(self):
        process = SimpleNamespace(poll=lambda: 1, returncode=1)

        with pytest.raises(RuntimeError, match=re.escape(str(preview_dev_server.LOG))):
            preview_dev_server.wait_until_serving(process, answers=lambda host, port: False, sleep=lambda s: None)

    def test_gives_up_after_the_timeout(self):
        process = SimpleNamespace(poll=lambda: None, returncode=None)
        now = iter(range(0, 1000, 10))

        with pytest.raises(TimeoutError, match="8081"):
            preview_dev_server.wait_until_serving(process, timeout=30, answers=lambda host, port: False, sleep=lambda s: None, clock=lambda: next(now))


class TestEnsure:
    def test_refuses_a_port_that_already_answers(self, monkeypatch):
        monkeypatch.setattr(preview_dev_server.preview_process, "answers", lambda host, port: True)

        with pytest.raises(RuntimeError, match="something already answers on port 8081; end it first"):
            preview_dev_server.ensure()


class TestStart:
    def test_gives_gradle_the_stats_proxy_in_its_environment(self, monkeypatch, tmp_path):
        launched = capture_popen(monkeypatch, tmp_path)

        preview_dev_server.start("http://netmon.local")

        assert launched["env"]["NETMON_STATS_PROXY"] == "http://netmon.local"

    def test_leaves_the_variable_out_without_a_proxy(self, monkeypatch, tmp_path):
        monkeypatch.delenv("NETMON_STATS_PROXY", raising=False)
        launched = capture_popen(monkeypatch, tmp_path)

        preview_dev_server.start()

        assert "NETMON_STATS_PROXY" not in launched["env"]


class TestWebpackConfig:
    def test_serves_on_the_port_the_preview_expects(self):
        config = (preview_dev_server.ROOT / "webpack.config.d" / "dev-server.js").read_text()

        match = re.search(r"port:\s*(?P<port>\d+)", config)
        assert match and int(match["port"]) == preview_dev_server.PORT

    def test_has_the_page_reach_the_dev_server_where_it_was_loaded_from(self):
        config = (preview_dev_server.ROOT / "webpack.config.d" / "dev-server.js").read_text()

        assert "webSocketURL: 'auto://0.0.0.0:0/ws'" in config

    def test_proxies_the_stats_file_to_the_board_the_session_names(self):
        config = (preview_dev_server.ROOT / "webpack.config.d" / "dev-server.js").read_text()

        assert "process.env.NETMON_STATS_PROXY" in config
        assert "'/stats.json'" in config


def capture_popen(monkeypatch, tmp_path):
    launched = {}
    monkeypatch.setattr(preview_dev_server, "LOG", tmp_path / "gradle.log")
    monkeypatch.setattr(preview_dev_server.subprocess, "Popen", lambda argv, **kwargs: launched.update(kwargs) or SimpleNamespace(pid=1))
    return launched
