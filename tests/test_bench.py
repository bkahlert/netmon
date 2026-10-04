import json

import pytest

import scan_fixtures
from bench import SCAN_TOPIC, scenario

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


T0 = 1759450000
