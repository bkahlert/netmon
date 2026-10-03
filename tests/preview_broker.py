"""The preview's broker: Mosquitto with the board's configuration in a container, holding the fixture as retained messages."""
import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import preview_process
import scan_fixtures

ROOT = Path(__file__).resolve().parents[1]
CONF = ROOT / "packages" / "netmon-scanner" / "conf" / "mosquitto-netmon.conf"
IMAGE = "docker.io/library/eclipse-mosquitto:2"
CONTAINER = "netmon-preview-broker"
WEBSOCKET_PORT = 8080
FIXTURE = "fixture"
DEVICE = "device"
EXTERNAL = "external"


@dataclass(frozen=True)
class Broker:
    kind: str
    host: str
    port: int

    @property
    def managed(self) -> bool:
        return self.kind == FIXTURE

    @property
    def address(self) -> str:
        return f"{self.host}:{self.port}"

    def describe(self) -> str:
        if self.kind == FIXTURE:
            return f"fixture on {self.address}"
        if self.kind == DEVICE:
            return f"the device's own, {self.address} on the board"
        return self.address


def parse_broker(text: str | None) -> Broker:
    if text in (None, "", FIXTURE):
        return Broker(FIXTURE, "localhost", WEBSOCKET_PORT)
    if text == DEVICE:
        return Broker(DEVICE, "127.0.0.1", WEBSOCKET_PORT)
    host, separator, port = text.rpartition(":")
    if not separator or not host or not port.isdigit() or not 0 < int(port) < 65536:
        raise ValueError(f"BROKER must be fixture, device or HOST:PORT, not {text!r}")
    return Broker(EXTERNAL, host, int(port))


def run_command(broker: Broker) -> list[str]:
    return [
        "podman", "run", "--rm", "--detach", "--name", CONTAINER,
        "--publish", f"127.0.0.1:{broker.port}:{WEBSOCKET_PORT}",
        "--volume", f"{CONF}:/mosquitto/config/mosquitto.conf:ro",
        IMAGE,
    ]


def publish_command(topic: str, scan: dict) -> list[str]:
    return ["podman", "exec", CONTAINER, "mosquitto_pub", "-h", "127.0.0.1", "-p", "1883", "-r", "-t", topic, "-m", json.dumps(scan)]


def ensure(broker: Broker, scan: scan_fixtures.Scan) -> None:
    """Starts the container and publishes the fixture; raises RuntimeError when something already answers on the broker's port."""
    if preview_process.answers(broker.host, broker.port):
        raise RuntimeError(f"port {broker.port} is taken; to use the broker there, run with BROKER=localhost:{broker.port}")
    result = subprocess.run(run_command(broker), capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"podman could not start the broker on {broker.address}: {result.stderr.strip()}")
    try:
        publish(scan_fixtures.scans(scan.sources, scan.recent, scan.stable))
    except BaseException:
        stop()
        raise


def publish(scan_by_topic: dict[str, dict], attempts: int = 40, pause: float = 0.25) -> None:
    for topic, scan in scan_by_topic.items():
        for _ in range(attempts):
            result = subprocess.run(publish_command(topic, scan), capture_output=True, text=True, check=False)
            if result.returncode == 0:
                break
            time.sleep(pause)
        else:
            raise RuntimeError(f"the broker did not accept {topic}: {result.stderr.strip()}")


def stop() -> None:
    subprocess.run(["podman", "stop", "--time", "2", CONTAINER], capture_output=True, check=False)


def main(environ=os.environ) -> int:
    try:
        broker = parse_broker(environ.get("BROKER"))
        scan = scan_fixtures.parse_scan(environ.get("SCAN") or "14+39")
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2
    if not broker.managed:
        print(f"BROKER={environ['BROKER']} is not the fixture, so there is nothing to run", file=sys.stderr)
        return 2
    preview_process.raise_on_sigterm()
    try:
        ensure(broker, scan)
    except RuntimeError as error:
        print(error, file=sys.stderr)
        return 2
    try:
        print(f"started the broker on {broker.address}; Ctrl-C ends it", file=sys.stderr)
        preview_process.until_interrupted()
    finally:
        stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
