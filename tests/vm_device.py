"""The device directory tier 2 boots: the sample device file with the testkit's user and the local apt repository."""
from pathlib import Path

from pihero_testkit import device_file

ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "devices" / "sample" / "user-data"
OUT = ROOT / "dist" / "vm-device"
SOURCE = "/etc/apt/sources.list.d/netmon.sources"


def render(sample: str, key: str) -> str:
    return device_file.with_source(device_file.with_user(sample, key), SOURCE)


def write(out: Path = OUT, sample: Path = SAMPLE) -> Path:
    return device_file.write(out, render(sample.read_text(), device_file.PUBLIC_KEY.read_text().strip()))


if __name__ == "__main__":
    print(write())
