"""Pure helpers for the preview's kiosk: its page URL, its settings and the Web Inspector's address."""
import re

GUEST_HOST = "10.0.2.2"
LOOPBACK = ("localhost", "127.0.0.1", "::1")
INSPECTOR = re.compile(r"window\.open\('Main\.html\?ws=' \+ window\.location\.host \+ '(?P<path>/socket/[^']+)'")


def guest_host(host: str) -> str:
    return GUEST_HOST if host in LOOPBACK else host


def page_url(dev_port: int, broker_host: str, broker_port: int) -> str:
    return f"http://{GUEST_HOST}:{dev_port}/?broker.host={guest_host(broker_host)}&broker.port={broker_port}"


def session_kiosk_conf(current: str, url: str, inspector_port: int) -> str:
    """Returns the kiosk.conf `current` with its URL replaced, developer extras added to cog's arguments and the inspector turned on."""
    lines, has_url, has_args = [], False, False
    for line in current.splitlines():
        if line.startswith("URL="):
            line, has_url = f"URL={url}", True
        elif line.startswith('COG_ARGS="'):
            line, has_args = line.replace('COG_ARGS="', 'COG_ARGS="--enable-developer-extras=true ', 1), True
        lines.append(line)
    if not (has_url and has_args):
        raise ValueError('kiosk.conf has no URL= line or no COG_ARGS="…" line to change')
    lines += [f"WEBKIT_INSPECTOR_HTTP_SERVER=127.0.0.1:{inspector_port}", "GSETTINGS_BACKEND=memory"]
    return "\n".join(lines) + "\n"


def inspector_url(listing: str, address: str) -> str | None:
    """Returns the address of the Web Inspector for the first target in the inspector's page list, or None if the list has none."""
    match = INSPECTOR.search(listing)
    return f"http://{address}/Main.html?ws={address}{match['path']}" if match else None


def inspect_app(value: str) -> str | None:
    return None if value in ("", "0") else value


def open_command(app: str, url: str) -> list[str]:
    return ["open", "-a", app, url]
