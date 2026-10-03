"""A scripted MQTT broker for Playwright's WebKit and the geometry helpers of the layout test."""
import json
import threading
import time
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

CONNACK = bytes([0x20, 0x02, 0x00, 0x00])
PINGRESP = bytes([0xD0, 0x00])
VENDORS = ["Apple", "Espressif", "Raspberry Pi Foundation", "AVM Audiovisuelles Marketing und Computersysteme GmbH", None, "HP"]
NAMES = ["printer.local.", "NPID96FF6", None, "openclaw-(690).local.", "indoorcam", "52540003C3310000.local.", "Shi"]
MODELS = ["Mac14,8", None, "AppleTV3,2", "AirPort10,115", None, "AirPods3,1"]


class Page:
    def __init__(self, directory: Path):
        handler = partial(QuietHandler, directory=str(directory))
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.port = self.server.server_address[1]

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self.port}/?broker.host=127.0.0.1&broker.port=1"

    def close(self):
        self.server.shutdown()


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def scans(sources: int, recent: int, stable: int, now: int | None = None) -> dict[str, dict]:
    now = now or int(time.time())
    result = {}
    for s in range(sources):
        topic = "dt/netmon/node/wlan0/10.0.0.1/24/scan" if s == 0 else f"dt/netmon/node{s}/eth{s}/10.{s}.0.1/24/scan"
        hosts = [host(s, i, now - 30 - i) for i in range(recent)] + [host(s, recent + i, now - 7200 - i) for i in range(stable)]
        result[topic] = {"event": "scan", "type": "completed", "hosts": hosts, "timestamp": now - 1}
    return result


def host(source: int, index: int, since: int) -> dict:
    entry = {"ip": f"10.{source}.{index // 250}.{index % 250 + 1}", "status": "down" if index % 7 == 3 else "up", "since": since}
    for key, values in (("name", NAMES), ("vendor", VENDORS), ("model", MODELS)):
        value = values[index % len(values)]
        if value:
            entry[key] = value
    return entry


def broker(scan_by_topic: dict[str, dict]):
    """Return a handler for `page.route_web_socket` that answers CONNECT, SUBSCRIBE and PINGREQ and publishes the scans."""

    def handle(route):
        pending = bytearray()

        def on_message(message):
            pending.extend(message if isinstance(message, bytes) else message.encode("latin-1"))
            for kind in packets(pending):
                if kind == 1:
                    route.send(CONNACK)
                elif kind == 8:
                    route.send(bytes([0x90, 0x03, 0x00, 0x01, 0x01]))
                    for topic, scan in scan_by_topic.items():
                        route.send(publish(topic, json.dumps(scan)))
                elif kind == 12:
                    route.send(PINGRESP)

        route.on_message(on_message)

    return handle


def packets(pending: bytearray):
    """Yield the type of each complete MQTT packet in `pending` and remove it."""
    while len(pending) >= 2:
        length, multiplier, i = 0, 1, 1
        while True:
            if i >= len(pending):
                return
            byte = pending[i]
            i += 1
            length += (byte & 127) * multiplier
            multiplier *= 128
            if not byte & 128:
                break
        if len(pending) < i + length:
            return
        kind = pending[0] >> 4
        del pending[: i + length]
        yield kind


def publish(topic: str, payload: str) -> bytes:
    name = topic.encode()
    body = len(name).to_bytes(2, "big") + name + payload.encode()
    size, remaining = len(body), bytearray()
    while True:
        byte, size = size % 128, size // 128
        remaining.append(byte | (128 if size else 0))
        if not size:
            break
    return bytes([0x30]) + bytes(remaining) + body


GEOMETRY = """() => {
  const box = e => { const r = e.getBoundingClientRect(); return {l: r.left, t: r.top, r: r.right, b: r.bottom}; };
  const scans = [...document.querySelectorAll('.scan__hosts')].map(e => e.closest('.networks > div > div'));
  return {
    viewport: {w: innerWidth, h: innerHeight},
    scroll: {w: document.documentElement.scrollWidth, h: document.documentElement.scrollHeight},
    scans: scans.map(box),
    hosts: [...document.querySelectorAll('.host')].map(h => ({
      ...box(h),
      scan: scans.indexOf(h.closest('.networks > div > div')),
      section: h.closest('.hosts').classList.contains('hosts--stable') ? 'stable' : 'unstable',
      fontSize: parseFloat(getComputedStyle(h).fontSize),
    })),
    zoomed: document.querySelectorAll('[style*=zoom], [data-zoomed]').length,
    card: (c => ({borderTopWidth: c.borderTopWidth, borderTopLeftRadius: c.borderTopLeftRadius}))(getComputedStyle(scans[0])),
  };
}"""


def overlapping(hosts: list[dict], tolerance: float = 0.5) -> list[tuple[int, int]]:
    found = []
    for i, a in enumerate(hosts):
        for j in range(i + 1, len(hosts)):
            b = hosts[j]
            if a["l"] < b["r"] - tolerance and b["l"] < a["r"] - tolerance and a["t"] < b["b"] - tolerance and b["t"] < a["b"] - tolerance:
                found.append((i, j))
    return found


def outside(hosts: list[dict], boxes: list[dict], tolerance: float = 1.0) -> list[int]:
    found = []
    for i, h in enumerate(hosts):
        s = boxes[h["scan"]]
        if h["l"] < s["l"] - tolerance or h["t"] < s["t"] - tolerance or h["r"] > s["r"] + tolerance or h["b"] > s["b"] + tolerance:
            found.append(i)
    return found
