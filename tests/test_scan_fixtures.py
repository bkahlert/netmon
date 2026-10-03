import json

import pytest

import scan_fixtures
from scan_fixtures import Scan

pytestmark = pytest.mark.tier0
NOW = 1_800_000_000


class TestParseScan:
    @pytest.mark.parametrize("text, expected", [("14+39", Scan(14, 39, 1)), ("14+39x2", Scan(14, 39, 2)), ("0+5", Scan(0, 5, 1))])
    def test_reads_the_counts(self, text, expected):
        scan = scan_fixtures.parse_scan(text)

        assert scan == expected

    @pytest.mark.parametrize("text", ["", "14", "14+", "+39", "14+39x", "14+39x0", " 14+39", "14+39 ", "a+b", "14+39x2x3"])
    def test_rejects_anything_else(self, text):
        with pytest.raises(ValueError, match="SCAN must be"):
            scan_fixtures.parse_scan(text)


class TestScans:
    def test_publishes_each_source_under_its_own_topic(self):
        found = scan_fixtures.scans(2, 1, 1, now=NOW)

        assert list(found) == ["dt/netmon/node/wlan0/10.0.0.1/24/scan", "dt/netmon/node1/eth1/10.1.0.1/24/scan"]

    def test_has_the_recent_hosts_first_and_the_stable_ones_older_than_an_hour(self):
        scan = next(iter(scan_fixtures.scans(1, 2, 3, now=NOW).values()))

        ages = [NOW - host["since"] for host in scan["hosts"]]
        assert [age < 3600 for age in ages] == [True, True, False, False, False]

    def test_is_a_completed_scan_event_that_serialises_to_json(self):
        scan = next(iter(scan_fixtures.scans(1, 1, 1, now=NOW).values()))

        decoded = json.loads(json.dumps(scan))
        assert (decoded["event"], decoded["type"], decoded["timestamp"]) == ("scan", "completed", NOW - 1)
