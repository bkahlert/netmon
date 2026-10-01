"""The device directory tier 2 boots: the sample device file with the testkit's user and the local apt repository."""
import re
from importlib.resources import files
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "devices" / "sample" / "user-data"
OUT = ROOT / "dist" / "vm-device"
PUBLIC_KEY = Path(str(files("pihero_testkit") / "keys" / "pihero-testkit.pub"))
USER = "pihero"
REPO_URL = "http://10.0.2.2:8000/"
NETMON_SOURCE = """\
  - path: /etc/apt/sources.list.d/netmon.sources
    content: |
      Types: deb
      URIs: {url}
      Suites: ./
      Trusted: yes
"""


def render(sample: str, key: str, user: str = USER, url: str = REPO_URL) -> str:
    users = block(sample, "users:")
    if users.count("  - name: ") != 1:
        raise ValueError("expected one user in the sample device file")
    renamed = re.sub(r"^(?P<prefix>  - name: ).*$", lambda m: m["prefix"] + user, users, count=1, flags=re.M)
    rekeyed, keys = re.subn(r"^(?P<prefix>    ssh_authorized_keys:\n      - ).*$", lambda m: m["prefix"] + key, renamed, count=1, flags=re.M)
    if keys == 0:
        raise ValueError("expected an ssh_authorized_keys entry in the sample device file")
    text = sample.replace(users, rekeyed)
    return text.replace(block(text, "  - path: /etc/apt/sources.list.d/netmon.sources"), NETMON_SOURCE.format(url=url))


def block(text: str, start: str) -> str:
    """Return the line equal to `start` and every following line indented deeper than it."""
    lines = text.splitlines(keepends=True)
    try:
        begin = next(i for i, line in enumerate(lines) if line.rstrip("\n") == start)
    except StopIteration:
        raise ValueError(f"the device file has no line {start!r}") from None
    indent = len(start) - len(start.lstrip(" "))
    end = begin + 1
    while end < len(lines) and (not lines[end].strip() or len(lines[end]) - len(lines[end].lstrip(" ")) > indent):
        end += 1
    return "".join(lines[begin:end])


def write(out: Path = OUT, sample: Path = SAMPLE) -> Path:
    out.mkdir(parents=True, exist_ok=True)
    (out / "user-data").write_text(render(sample.read_text(), key=PUBLIC_KEY.read_text().strip()))
    return out


if __name__ == "__main__":
    print(write())
