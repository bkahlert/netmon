import json
import subprocess
from pathlib import Path
import urllib.request

import pytest

from netmon_dev.bench import command as bench
from netmon_dev.bench.command import SCAN_TOPIC, Variant, order, page_url, parse_runs, parse_variants, run_all, run_timeline, scenario
from netmon_dev.bench.fixtures import sample
from netmon_dev.preview import scan_fixtures
from pihero_testkit.preview import board
from netmon_dev.bench.report import Run
from tests.browser.layout import Page

pytestmark = pytest.mark.tier0


class TestScenario:
    def test_starts_with_the_53_hosts_of_the_fixture_at_t0(self):
        result = scenario(T0)

        assert result[0] == (0, scan_fixtures.scans(1, 14, 39, now=T0)[SCAN_TOPIC])
        assert len(result[0][1]["hosts"]) == 53

    def test_changes_eight_hosts_60_s_later(self):
        result = scenario(T0)

        (_, first), (offset, second) = result
        before = {host["ip"]: host for host in first["hosts"]}
        after = {host["ip"]: host for host in second["hosts"]}
        kept = before.keys() & after.keys()
        assert offset == 60
        assert second["timestamp"] == T0 + 60
        assert len([ip for ip in kept if (before[ip]["status"], after[ip]["status"]) == ("up", "down")]) == 3
        assert len([ip for ip in kept if (before[ip]["status"], after[ip]["status"]) == ("down", "up")]) == 3
        assert len(after.keys() - before.keys()) == 1
        assert len(before.keys() - after.keys()) == 1
        assert len([ip for ip in kept if before[ip] != after[ip]]) == 6

    def test_dates_every_change_at_its_scan(self):
        result = scenario(T0)

        (_, first), (_, second) = result
        before = {host["ip"]: host for host in first["hosts"]}
        changed = [host for host in second["hosts"] if before.get(host["ip"]) != host]
        assert {host["since"] for host in changed} == {T0 + 60}

    def test_changes_recent_and_stable_hosts(self):
        result = scenario(T0)

        (_, first), (_, second) = result
        after = {host["ip"]: host for host in second["hosts"]}
        changed = [host for host in first["hosts"] if after.get(host["ip"]) != host]
        assert {host["since"] > T0 - 3600 for host in changed} == {True, False}

    def test_is_the_same_for_the_same_t0(self):
        result = json.dumps(scenario(T0))

        assert result == json.dumps(scenario(T0))


class TestParseVariants:
    def test_is_the_working_tree_by_default(self):
        result = parse_variants(None, fake_git(dirty=""))

        assert result == [Variant(".", HEAD)]

    def test_resolves_refs_to_their_commits_in_the_order_given(self):
        result = parse_variants("main .", fake_git(dirty=" M README.md"))

        assert result == [Variant("main", MAIN), Variant(".", HEAD, dirty=True)]

    def test_rejects_an_unknown_ref(self):
        with pytest.raises(ValueError, match="'nope', which is no commit here"):
            parse_variants("main nope", fake_git())

    def test_rejects_two_refs_of_one_commit(self):
        with pytest.raises(ValueError, match="main and origin/main are both a33715a"):
            parse_variants("main origin/main", fake_git())

    def test_rejects_a_ref_given_twice(self):
        with pytest.raises(ValueError, match="names main twice"):
            parse_variants("main main", fake_git())


class TestVariant:
    def test_labels_a_ref_with_its_short_sha(self):
        assert Variant("main", MAIN).label == "main a33715a"

    def test_labels_a_ref_that_is_its_sha_once(self):
        assert (Variant("a33715a", MAIN).label, Variant(MAIN, MAIN).label) == ("a33715a", "a33715a")

    def test_labels_a_changed_working_tree_dirty(self):
        assert Variant(".", HEAD, dirty=True).label == ". f5320cb+dirty"

    def test_keeps_the_working_tree_apart_from_its_commit(self):
        assert (Variant(".", HEAD).directory, Variant("HEAD", HEAD).directory) == ("working-tree", HEAD)


class TestParseRuns:
    @pytest.mark.parametrize("text", [None, ""])
    def test_is_one_by_default(self, text):
        assert parse_runs(text) == 1

    def test_reads_a_count(self):
        assert parse_runs("3") == 3

    @pytest.mark.parametrize("text", ["0", "-1", "two", "1.5"])
    def test_rejects_anything_but_a_positive_count(self, text):
        with pytest.raises(ValueError, match="RUNS must be a whole number of at least 1"):
            parse_runs(text)


class TestOrder:
    def test_interleaves_the_variants(self):
        a, b = Variant("a", "1" * 40), Variant("b", "2" * 40)

        result = order([a, b], 3)

        assert result == [a, b, a, b, a, b]


class TestRunTimeline:
    def test_clears_the_scan_before_the_kiosk_restarts(self):
        events, stream = [], FakeStream(every_five_seconds(0, 200), arriving_during_install=3)

        run_timeline(stream, clear=lambda: events.append("clear"), install=lambda: events.append("install") or stream.install(), publish=lambda scan: events.append("publish"), payloads=[])

        assert events == ["clear", "install", "publish", "publish"]

    def test_takes_the_samples_of_the_restart_as_the_load(self):
        stream = FakeStream(every_five_seconds(0, 200), arriving_during_install=3)

        result = run_timeline(stream, clear=lambda: None, install=stream.install, publish=lambda scan: None, payloads=[])

        assert result.before.at == 0
        assert [s.at for s in result.load] == [5, 10, 15]
        assert result.samples[0].at == 20

    def test_publishes_each_scan_right_after_its_sample_and_ends_on_one(self):
        published, stream = [], FakeStream(every_five_seconds(0, 200), arriving_during_install=3)

        result = run_timeline(stream, clear=lambda: None, install=stream.install, publish=lambda scan: published.append((stream.received, scan)), payloads=[])

        assert [result.samples[i].at for i in result.boundaries] == [20, 80]
        assert published == [(5, scenario(20)[0][1]), (17, scenario(20)[1][1])]
        assert result.samples[-1].at == 170

    def test_waits_past_a_missing_sample(self):
        samples = [s for s in every_five_seconds(0, 200) if s.at != 80]
        stream = FakeStream(samples, arriving_during_install=3)

        result = run_timeline(stream, clear=lambda: None, install=stream.install, publish=lambda scan: None, payloads=[])

        assert result.samples[result.boundaries[1]].at == 85

    def test_drops_what_arrived_before_the_run(self):
        stream = FakeStream(every_five_seconds(0, 200), arriving_during_install=3, queued=[sample(-5)])
        payloads = []

        result = run_timeline(stream, clear=lambda: None, install=stream.install, publish=lambda scan: None, payloads=payloads)

        assert result.before.at == 0
        assert payloads[0] == b"0"

    def test_raises_on_silence_and_keeps_the_payloads(self):
        stream = FakeStream(every_five_seconds(0, 100), arriving_during_install=3)
        payloads = []

        with pytest.raises(ConnectionError):
            run_timeline(stream, clear=lambda: None, install=stream.install, publish=lambda scan: None, payloads=payloads)

        assert payloads[-1] == b"100"

    def test_keeps_the_load_time_the_install_returns(self):
        stream = FakeStream(every_five_seconds(0, 200), arriving_during_install=3)

        result = run_timeline(stream, clear=lambda: None, install=lambda: stream.install() or 21.0, publish=lambda scan: None, payloads=[])

        assert result.load_time == 21.0


class TestLoadTime:
    def test_runs_from_the_kiosks_start_to_the_pages_load(self):
        result = bench.load_time(STARTED + "1791140200.5 netmon cog[71619]: Using WPE backend\n" + LOADED)

        assert result == pytest.approx(21.050855)

    def test_counts_from_the_last_start_before_the_load(self):
        result = bench.load_time("1791140150.0 netmon systemd[1]: Started pihero-kiosk.service - Pi Hero.\n" + STARTED + LOADED)

        assert result == pytest.approx(21.050855)

    def test_is_none_without_a_load(self):
        result = bench.load_time(STARTED)

        assert result is None

    def test_skips_lines_without_a_time(self):
        result = bench.load_time("-- No entries --\n")

        assert result is None


class TestPageUrl:
    def test_reaches_the_variant_and_the_broker_through_the_boards_forwards(self):
        result = page_url(Variant("main", MAIN))

        assert result == f"http://127.0.0.1:18082/{MAIN}/?broker.host=127.0.0.1&broker.port=18080"

    def test_serves_a_variants_page_and_its_assets_under_its_directory(self, tmp_path):
        (tmp_path / MAIN).mkdir()
        (tmp_path / MAIN / "index.html").write_text("<html>")
        (tmp_path / MAIN / "netmon.js").write_text("js")
        page = Page(tmp_path)
        try:
            index = urllib.request.urlopen(f"http://127.0.0.1:{page.port}/{MAIN}/?broker.host=127.0.0.1&broker.port=18080").read()
            script = urllib.request.urlopen(f"http://127.0.0.1:{page.port}/{MAIN}/netmon.js").read()
        finally:
            page.close()

        assert (index, script) == (b"<html>", b"js")


class TestRunAll:
    def test_writes_the_report_after_every_run(self):
        written = []
        a, b = Variant("a", "1" * 40), Variant("b", "2" * 40)

        run_all([a, b], lambda n, v: Run(n, v.label, None, "x"), write=lambda runs: written.append(len(runs)), report=lambda line: None)

        assert written == [1, 2]

    def test_keeps_the_report_of_the_finished_runs_on_ctrl_c(self):
        written = []

        def one_run(number, variant):
            if number == 3:
                raise KeyboardInterrupt
            return Run(number, variant.label, None, "x")

        with pytest.raises(KeyboardInterrupt):
            run_all(order([Variant("a", "1" * 40)], 3), one_run, write=lambda runs: written.append([r.number for r in runs]), report=lambda line: None)

        assert written[-1] == [1, 2]

    def test_reports_a_line_per_run(self):
        lines = []

        run_all([Variant("a", "1" * 40)], lambda n, v: Run(n, v.label, None, "the board rebooted"), write=lambda runs: None, report=lines.append)

        assert lines == ["run 1/1 a 1111111: failed: the board rebooted"]


class TestBundle:
    def test_replaces_a_bundle_directory_without_a_page(self, tmp_path, monkeypatch):
        monkeypatch.setattr(bench, "BUNDLES", tmp_path / "bundles")
        monkeypatch.setattr(bench, "SOURCES", tmp_path / "src")
        (tmp_path / "bundles" / MAIN).mkdir(parents=True)
        (tmp_path / "bundles" / MAIN / "stale.js").write_text("old")

        result = bench.bundle(Variant("main", MAIN), run=fake_build)

        assert sorted(path.name for path in result.iterdir()) == ["index.html"]


class TestCheck:
    def test_refuses_another_session_on_the_kiosk(self, monkeypatch):
        monkeypatch.setattr(bench.process, "answers", lambda host, port: False)

        with pytest.raises(RuntimeError, match="netmon-preview.conf"):
            bench.check(FakeSession(dropins="netmon-preview.conf\n"))

    def test_takes_over_a_session_of_its_own(self, monkeypatch):
        monkeypatch.setattr(bench.process, "answers", lambda host, port: False)

        result = bench.check(FakeSession(dropins="netmon-bench-preview.conf\n"))

        assert result is None

    def test_refuses_a_board_without_metrics(self, monkeypatch):
        monkeypatch.setattr(bench.process, "answers", lambda host, port: False)

        with pytest.raises(RuntimeError, match="no active netmon-metrics.service"):
            bench.check(FakeSession(metrics="inactive"))

    def test_refuses_a_taken_port_of_the_workstation(self, monkeypatch):
        monkeypatch.setattr(bench.process, "answers", lambda host, port: port == 8080)

        with pytest.raises(RuntimeError, match="port 8080 is taken"):
            bench.check(FakeSession())


class TestMain:
    def test_needs_a_target(self, capsys):
        status = bench.main({})

        assert status == 2
        assert "make bench TARGET=" in capsys.readouterr().err

    def test_names_a_malformed_runs(self, capsys):
        status = bench.main({"TARGET": "pi@netmon.local", "RUNS": "0"})

        assert status == 2
        assert "RUNS must be" in capsys.readouterr().err

    def test_checks_the_board_before_any_build(self, capsys, monkeypatch):
        built = []
        monkeypatch.setattr(bench.process, "raise_on_sigterm", lambda: None)
        monkeypatch.setattr(bench.board, "Session", lambda target, name, log: FakeSession(kiosk_error=RuntimeError("cannot reach pi@nope over ssh")))
        monkeypatch.setattr(bench, "bundle", built.append)

        status = bench.main({"TARGET": "pi@nope"})

        assert (status, built) == (2, [])
        assert "cannot reach pi@nope" in capsys.readouterr().err

    def test_names_a_missing_program(self, capsys, monkeypatch):
        monkeypatch.setattr(bench.process, "raise_on_sigterm", lambda: None)
        monkeypatch.setattr(bench.board, "Session", lambda target, name, log: FakeSession(kiosk_error=FileNotFoundError(2, "No such file or directory", "ssh")))

        status = bench.main({"TARGET": "pi@netmon.local"})

        assert status == 2
        assert "'ssh'" in capsys.readouterr().err


class FakeStream:
    def __init__(self, samples, arriving_during_install: int, queued=()):
        self.future, self.queued, self.during_install, self.received = list(samples), list(queued), arriving_during_install, 0

    def install(self):
        self.queued += self.future[:self.during_install]
        del self.future[:self.during_install]

    def receive(self):
        if self.queued:
            return self.give(self.queued.pop(0))
        if not self.future:
            raise ConnectionError("no metrics for 15 s")
        return self.give(self.future.pop(0))

    def pending(self):
        result = [self.give(s) for s in self.queued]
        self.queued = []
        return result

    def give(self, s):
        self.received += 1
        return s, str(int(s.at)).encode()


def every_five_seconds(first: int, last: int):
    return [sample(at) for at in range(first, last + 1, 5)]


class FakeSession:
    def __init__(self, dropins: str = "", metrics: str = "active", kiosk_error: Exception | None = None):
        self.target, self.dropin = "pi@netmon.local", f"{board.DROPIN_DIR}/netmon-bench-preview.conf"
        self.dropins, self.metrics, self.kiosk_error = dropins, metrics, kiosk_error

    def check_kiosk(self):
        if self.kiosk_error:
            raise self.kiosk_error

    def ssh(self, remote: str) -> subprocess.CompletedProcess:
        if remote.startswith("systemctl is-active"):
            return subprocess.CompletedProcess(remote, 0 if self.metrics == "active" else 3, stdout=f"{self.metrics}\n", stderr="")
        if remote.startswith("ls "):
            return subprocess.CompletedProcess(remote, 0, stdout=self.dropins, stderr="")
        raise AssertionError(remote)


def fake_build(command, cwd, **_):
    if command[0] == "git" and command[1:3] == ["worktree", "add"]:
        Path(command[4]).mkdir(parents=True)
    if command == bench.GRADLE:
        (Path(cwd) / bench.DIST).mkdir(parents=True)
        (Path(cwd) / bench.DIST / "index.html").write_text("<html>")


def fake_git(dirty: str = ""):
    commits = {"HEAD": HEAD, "main^{commit}": MAIN, "origin/main^{commit}": MAIN}

    def git(*args: str) -> str:
        if args == ("rev-parse", "HEAD"):
            return HEAD
        if args == ("status", "--porcelain"):
            return dirty
        if args[:3] == ("rev-parse", "--verify", "--quiet") and args[3] in commits:
            return commits[args[3]]
        raise subprocess.CalledProcessError(1, ["git", *args])

    return git


HEAD = "f5320cb711ad5e456b2d04fd6a8d5451ddf44128"
MAIN = "a33715a" + "0" * 33
STARTED = "1791140198.086536 netmon systemd[1]: Started pihero-kiosk.service - Pi Hero: the kiosk page on the display.\n"
LOADED = "1791140219.137391 netmon cog[71619]: <http://127.0.0.1:18082/x/?broker.host=127.0.0.1&broker.port=18080> Loaded successfully.\n"
T0 = 1759450000
