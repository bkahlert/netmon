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
DEFAULT_BROKER = "localhost:8080"
WEBSOCKET_PORT = 8080


@dataclass(frozen=True)
class Broker:
    host: str
    port: int
    managed: bool

    @property
    def address(self) -> str:
        return f"{self.host}:{self.port}"


def parse_broker(text: str) -> Broker:
    host, separator, port = text.rpartition(":")
    if not separator or not host or not port.isdigit() or not 0 < int(port) < 65536:
        raise ValueError(f"BROKER must be HOST:PORT, not {text!r}")
    return Broker(host, int(port), managed=text == DEFAULT_BROKER)


def run_command(broker: Broker) -> list[str]:
    return [
        "podman", "run", "--rm", "--detach", "--name", CONTAINER,
        "--publish", f"127.0.0.1:{broker.port}:{WEBSOCKET_PORT}",
        "--volume", f"{CONF}:/mosquitto/config/mosquitto.conf:ro",
        IMAGE,
    ]


def publish_command(topic: str, scan: dict) -> list[str]:
    return ["podman", "exec", CONTAINER, "mosquitto_pub", "-h", "127.0.0.1", "-p", "1883", "-r", "-t", topic, "-m", json.dumps(scan)]


def ensure(broker: Broker, scan: scan_fixtures.Scan) -> bool:
    """Starts the container and publishes the fixture unless something already answers on the broker's address; returns whether it started the container."""
    if preview_process.answers(broker.host, broker.port):
        return False
    result = subprocess.run(run_command(broker), capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"podman could not start the broker on {broker.address}: {result.stderr.strip()}")
    try:
        publish(scan_fixtures.scans(scan.sources, scan.recent, scan.stable))
    except BaseException:
        stop()
        raise
    return True


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
        broker = parse_broker(environ.get("BROKER") or DEFAULT_BROKER)
        scan = scan_fixtures.parse_scan(environ.get("SCAN") or "14+39")
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2
    if not broker.managed:
        print(f"BROKER={broker.address} is not the preview's broker, so there is nothing to run", file=sys.stderr)
        return 2
    preview_process.raise_on_sigterm()
    started = False
    try:
        started = ensure(broker, scan)
        print(f"{'started' if started else 'found'} the broker on {broker.address}; Ctrl-C ends it", file=sys.stderr)
        preview_process.until_interrupted()
    finally:
        if started:
            stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
