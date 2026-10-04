"""make bench: the display's scripted benchmark on a board, each variant built on the workstation and served through the board's tunnel."""
import copy

import scan_fixtures

SCAN_TOPIC = "dt/netmon/node/wlan0/10.0.0.1/24/scan"
SCAN_TWO_AT = 60
END_AT = 150
GOES_DOWN = (0, 20, 30)
COMES_UP = (10, 24, 38)
GONE = 40


def scenario(t0: int) -> list[tuple[int, dict]]:
    """Return the scans of a run as (offset, scan) steps, every timestamp t0 plus an offset: the 14+39 fixture, then eight changes."""
    first = scan_fixtures.scans(1, 14, 39, now=t0)[SCAN_TOPIC]
    return [(0, first), (SCAN_TWO_AT, changed(first, t0 + SCAN_TWO_AT))]


def changed(scan: dict, at: int) -> dict:
    """Return `scan` as it is at `at`: three hosts down, three up, one new and one gone, each change since `at`."""
    hosts = copy.deepcopy(scan["hosts"])
    for index, status in [(i, "down") for i in GOES_DOWN] + [(i, "up") for i in COMES_UP]:
        hosts[index] |= {"status": status, "since": at}
    hosts = [host for index, host in enumerate(hosts) if index != GONE]
    hosts.append(scan_fixtures.host(0, len(scan["hosts"]), at))
    return {**scan, "hosts": hosts, "timestamp": at}
