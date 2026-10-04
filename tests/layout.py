"""A scripted MQTT broker for Playwright's WebKit and the geometry helpers of the layout test."""
import json
import threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

CONNACK = bytes([0x20, 0x02, 0x00, 0x00])
PINGRESP = bytes([0xD0, 0x00])


class Page:
    def __init__(self, directory: Path, port: int = 0):
        self.directory = directory
        handler = partial(QuietHandler, directory=str(directory))
        self.server = ThreadingHTTPServer(("127.0.0.1", port), handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.port = self.server.server_address[1]

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self.port}/?broker.host=127.0.0.1&broker.port=1"

    def url_of(self, path: str) -> str:
        return f"http://127.0.0.1:{self.port}/{path}"

    def loading_image(self) -> str:
        return next((self.directory / "images").glob("loading.*.svg")).relative_to(self.directory).as_posix()

    def close(self):
        self.server.shutdown()


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def broker(scan_by_topic: dict[str, dict]):
    """Return a handler for `page.route_web_socket` that answers CONNECT, SUBSCRIBE and PINGREQ and publishes the scans."""

    def handle(route):
        pending = bytearray()

        def on_message(message):
            pending.extend(message if isinstance(message, bytes) else message.encode("latin-1"))
            for kind, body in packets(pending):
                if kind == 1:
                    route.send(CONNACK)
                elif kind == 8:
                    route.send(suback(body))
                    for topic, scan in scan_by_topic.items():
                        route.send(publish(topic, json.dumps(scan)))
                elif kind == 12:
                    route.send(PINGRESP)

        route.on_message(on_message)

    return handle


def packets(pending: bytearray):
    """Yield the type and the body after the fixed header of each complete MQTT packet in `pending`, and remove it."""
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
        body = bytes(pending[i : i + length])
        del pending[: i + length]
        yield kind, body


def suback(subscribe: bytes) -> bytes:
    """Return the SUBACK of a SUBSCRIBE body: its packet id and QoS 1 granted for each of its topic filters."""
    filters, i = 0, 2
    while i < len(subscribe):
        i += 2 + int.from_bytes(subscribe[i : i + 2], "big") + 1
        filters += 1
    return bytes([0x90, 2 + filters]) + subscribe[:2] + bytes([0x01] * filters)


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


def slowed(milliseconds: int) -> str:
    """Return a script that delays every task the page schedules by `milliseconds`, as a slower machine would."""
    return f"""(() => {{
  const delay = {milliseconds};
  const later = window.setTimeout.bind(window);
  window.setTimeout = (callback, wait, ...args) => later(callback, (wait || 0) + delay, ...args);
  const postWindow = window.postMessage.bind(window);
  window.postMessage = (...args) => later(() => postWindow(...args), delay);
  const postPort = MessagePort.prototype.postMessage;
  MessagePort.prototype.postMessage = function (...args) {{ later(() => postPort.apply(this, args), delay); }};
}})()"""


RENDERED = """({hosts, models}) => {
  const cards = [...document.querySelectorAll('.host')];
  return cards.length === hosts
    && cards.every(card => ['.host__name', '.host__vendor', '.host__ip', '.host__status'].every(part => card.querySelector(part)))
    && document.querySelectorAll('.host__model').length === models;
}"""

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
