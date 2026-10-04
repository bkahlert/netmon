import json
import subprocess

import pytest

import scan_fixtures
from bench import SCAN_TOPIC, Variant, order, parse_runs, parse_variants, scenario

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
        with pytest.raises(ValueError, match="twice"):
            parse_variants("main origin/main", fake_git())


class TestVariant:
    def test_labels_a_ref_with_its_short_sha(self):
        assert Variant("main", MAIN).label == "main a33715a"

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
T0 = 1759450000
