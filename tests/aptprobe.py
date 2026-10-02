"""The apt probe's pure parts: the transient unit's command line and the verdict over what the run changed."""
from sampling import UnitSample

HOOK = "/etc/apt/apt.conf.d/52netmon-dpkg"
UNIT = "netmon-apt-probe"
TERMINAL = {"exited", "failed", "dead"}


def command(package: str) -> str:
    """Return the systemd-run line that runs the update and reinstalls the package as an accounted unit that stays loaded after exit."""
    apt = f"apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --reinstall {package}"
    return f"systemd-run --unit={UNIT} --quiet -p MemoryAccounting=yes -p RemainAfterExit=yes sh -c '{apt}'"


def classify(sub_state: str, exit_status: str, boot_before: str, boot_after: str, before: dict[str, UnitSample], after: dict[str, UnitSample]) -> str:
    """Return `ok`, or one sentence naming the failure: a reset, an unreachable target, a timeout, apt's exit status, a unit stopped, restarted or killed."""
    if not boot_after:
        return "the target is unreachable after the run"
    if boot_after != boot_before:
        return "the target rebooted during the run"
    if sub_state not in TERMINAL:
        return f"apt did not finish within the timeout (unit state {sub_state!r})"
    if exit_status != "0":
        return f"apt exited with {exit_status}"
    for unit, was in before.items():
        now = after[unit]
        if now.active != "active":
            return f"{unit} is {now.active} after the run"
        if now.restarts != was.restarts:
            return f"{unit} restarted during the run"
        if now.oom_kills != was.oom_kills:
            return f"{unit} had an oom kill during the run"
    return "ok"
