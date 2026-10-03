# The preview in three flavors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `make preview-browser`, `make preview-vm` (alias `make preview`) and `make preview-device TARGET=user@host` show the display from Gradle's dev server in a browser tab, in the kiosk of a QEMU VM and in the kiosk of a real Pi, with the same `BROKER`, `SCAN` and `INSPECT` options and a Web Inspector on each.

**Architecture:** `tests/preview.py` keeps the shared steps (record, fixture broker, dev server, inspector wait, `INSPECT`, cleanup stack) and asks a *flavor* to show the page. A flavor is one of three small classes in `tests/preview_flavors.py`; the VM's is today's block moved out of `run()`, the browser's does nothing, the device's uses `tests/preview_board.py`: one `ssh -N` carries reverse forwards for the dev server and a Mac-side broker plus a forward for the inspector, and the kiosk is pointed at them by a volatile systemd drop-in under `/run`. `BROKER` becomes `fixture` (default, started and stopped by the session), `device` or `HOST:PORT`.

**Tech Stack:** Python 3.13 and pytest (`tests/`, `uv`), `pihero-testkit` (`pihero_testkit.ssh.command`, `KEEPALIVE`), OpenSSH, systemd drop-ins, Podman with `eclipse-mosquitto:2`, Gradle's `jsBrowserDevelopmentRun`, webpack-dev-server, GNU Make.

**Spec:** [2026-10-03-preview-flavors-design.md](../specs/2026-10-03-preview-flavors-design.md); it builds on [2026-10-03-preview-design.md](../specs/2026-10-03-preview-design.md).

## Global Constraints

- Ports on the Mac: broker `8080`, dev server `8081`, Web Inspector `2999`, all bound to `127.0.0.1`. On the board: reverse ports `18081` (dev server) and `18080` (Mac-side broker) bound to `127.0.0.1`; the board's own broker keeps `8080`, its lighttpd `80`.
- `BROKER`: unset, empty or `fixture` is the Mosquitto container with the `SCAN` fixture (started and stopped by the session; if its port answers, fail naming `BROKER=localhost:<port>`); `device` is the board's own broker and only `preview-device` takes it; `HOST:PORT` is used as it is, nothing started or stopped, and `localhost`, `127.0.0.1` and `::1` mean the Mac in every flavor. Anything else fails in one line: `BROKER must be fixture, device or HOST:PORT, not '<text>'`.
- `TARGET` is `user@host[:port]`: required by `preview-device`, refused by the other two. Empty variables count as unset.
- `SCAN` default `14+39`; `INSPECT` default `Safari`, `0` or empty opens nothing. `INSPECT` opens the page in the browser flavor and the kiosk's Web Inspector (`Main.html?ws=…`) in the VM and device flavors.
- Only one preview runs at a time: a second fails with `a preview is already running (process N); end it with Ctrl-C first`; a dev server answering on 8081 without a preview owning it fails with `something already answers on port 8081; end it first`. Nothing is reused implicitly.
- Nothing persistent changes on the board. Session files: `/run/netmon-preview/kiosk.conf` and `/run/systemd/system/pihero-kiosk.service.d/preview.conf` (`[Service]` and `EnvironmentFile=/run/netmon-preview/kiosk.conf`). Ending the session removes both, runs `daemon-reload` and restarts `pihero-kiosk`.
- The board's kiosk page is `http://127.0.0.1:18081/?broker.host=<host>&broker.port=<port>`: `127.0.0.1` and `18080` for a broker on the Mac, `127.0.0.1` and `8080` for `BROKER=device`, the given host and port for a non-loopback `HOST:PORT`.
- In the device flavor the dev server proxies `/stats.json` to `http://<host of TARGET>` through the environment variable `NETMON_STATS_PROXY`; without the variable nothing is proxied.
- The record `dist/preview/session.json` gains `device` (the `TARGET`) and `tunnel` (the ssh process id). The next start ends the tunnel and restores the board.
- Commits: Conventional Commits (see `~/.config/agents/rules/git.md`), scope `preview`, imperative lowercase header of at most 72 characters, no AI-attribution trailers, never on `main`. Work on branch `feat/preview-flavors`.
- Tests: Python, pytest, classes named after the subject, tests first and helpers last, no comments in tests, regexes with named groups. Pure logic is marked `tier0`; what needs QEMU or Podman is marked `preview` and runs by `make test-preview` only. The device flavor cannot run in CI.
- One Gradle build per project directory at a time (`~/.config/agents/rules/gradle.md`): the previews run one for as long as they live, so stop them before `make test-js`, `make test-layout` or any `./gradlew`, and never leave a background Gradle behind.
- Anything that publishes (pushing a branch, a pull request, a tag) waits for the user's go-ahead.

## Review Focus

Failure modes the spec implies that a happy path misses; each has its test in the task named on its line.

- Empty variables: `make preview-vm` passes `BROKER=`, `TARGET=` and `SCAN=` as empty strings and they must mean "unset", not "malformed" (Task 3).
- `TARGET` with a port, `pi@netmon.local:2222`: ssh gets `-p 2222` and the stats proxy and messages use only the host (Task 5).
- `BROKER=localhost:9000` or `127.0.0.1:9000` in the device flavor is a broker on the Mac and needs a reverse forward, while `netmon.local:9000` is not and must not get one (Task 5).
- A board whose `kiosk.conf` lacks `URL=` or `COG_ARGS="…"`, an unreachable board, and a board without `pihero-kiosk` each fail with one readable line and leave the board as it was (Task 5).
- An install that fails halfway, or a Ctrl-C during it, must still remove the drop-in and close the tunnel (Task 6).

---

### Task 0: Branch, spec correction, spec and plan committed

**Files:**
- Modify: `docs/superpowers/specs/2026-10-03-preview-flavors-design.md`
- Add: `docs/superpowers/specs/2026-10-03-preview-flavors-design.md`, `docs/superpowers/plans/2026-10-03-preview-flavors.md`

- [ ] **Step 1: Rebase the branch onto main**

```bash
cd /Users/bkahlert/Development/com.bkahlert/netmon
git switch feat/preview-flavors
git rebase main
git log --oneline -2
```

Expected: `13fc944` (or newer) is the base; the spec and this plan are untracked files and survive.

- [ ] **Step 2: Correct the reverse forward in the spec**

The plan treats a `HOST:PORT` with a loopback host as a broker on the Mac, which the spec's "fixture broker (only then)" does not say. Edit [the spec](../specs/2026-10-03-preview-flavors-design.md):

Replace
```
`-R 18080:127.0.0.1:8080` for a fixture broker (only then)
```
with
```
`-R 18080:127.0.0.1:<port>` for a broker on the Mac (only then: the fixture, or a `HOST:PORT` whose host is a loopback name)
```
and replace
```
`http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080` with the fixture, `…broker.port=8080` with
`BROKER=device`, and the given host and port with `HOST:PORT`.
```
with
```
`http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080` with a broker on the Mac, `…broker.port=8080` with
`BROKER=device`, and the given host and port with any other `HOST:PORT`.
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-10-03-preview-flavors-design.md
git commit -m "docs(spec): add the preview in three flavors"
git add docs/superpowers/plans/2026-10-03-preview-flavors.md
git commit -m "docs(plan): add the preview in three flavors"
```

---

### Task 1: The `BROKER` grammar and a fixture that does not reuse

**Files:**
- Modify: `tests/preview_broker.py` (replace the file's `Broker`, `parse_broker`, `ensure` and `main`)
- Modify: `tests/preview.py` (`Settings.from_environ`, the broker lines of `run`)
- Test: `tests/test_preview_broker.py`, `tests/test_preview.py` (`TestSettings`)

**Interfaces:**
- Produces: `preview_broker.FIXTURE`, `DEVICE`, `EXTERNAL` (the strings `"fixture"`, `"device"`, `"external"`); `Broker(kind: str, host: str, port: int)` with `.managed -> bool`, `.address -> str`, `.describe() -> str`; `parse_broker(text: str | None) -> Broker`; `ensure(broker, scan) -> None`, raising `RuntimeError` when the port answers.

- [ ] **Step 1: Write the failing tests**

In `tests/test_preview_broker.py` replace `TestParseBroker` with:

```python
@pytest.mark.tier0
class TestParseBroker:
    @pytest.mark.parametrize("text", [None, "", "fixture"])
    def test_is_the_fixture_on_the_macs_8080_by_default(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker == Broker(FIXTURE, "localhost", 8080)

    def test_is_the_boards_own_broker_on_its_loopback_for_device(self):
        broker = preview_broker.parse_broker("device")

        assert broker == Broker(DEVICE, "127.0.0.1", 8080)

    @pytest.mark.parametrize("text", ["localhost:8080", "127.0.0.1:8080", "localhost:9000", "netmon.local:8080"])
    def test_uses_a_host_and_port_as_it_is(self, text):
        broker = preview_broker.parse_broker(text)

        assert broker.kind == EXTERNAL
        assert broker.managed is False
        assert broker.address == text

    @pytest.mark.parametrize("text", ["netmon.local", ":8080", "host:", "host:abc", "host:0", "host:70000", "host:-1", "Fixture", "mock"])
    def test_rejects_anything_but_the_three_forms(self, text):
        with pytest.raises(ValueError, match="BROKER must be fixture, device or HOST:PORT"):
            preview_broker.parse_broker(text)


@pytest.mark.tier0
class TestBroker:
    @pytest.mark.parametrize("text, described", [("fixture", "fixture on localhost:8080"), ("device", "the device's own, 127.0.0.1:8080 on the board"), ("netmon.local:8080", "netmon.local:8080")])
    def test_describes_itself_for_the_ready_message(self, text, described):
        assert preview_broker.parse_broker(text).describe() == described


@pytest.mark.tier0
class TestEnsure:
    def test_refuses_a_port_that_already_answers_and_names_the_way_to_use_it(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen()
            port = server.getsockname()[1]

            with pytest.raises(RuntimeError, match=f"port {port} is taken; to use the broker there, run with BROKER=localhost:{port}"):
                preview_broker.ensure(Broker(FIXTURE, "127.0.0.1", port), Scan(1, 1, 1))
```

Change the imports at the top of the file to
```python
from preview_broker import DEVICE, EXTERNAL, FIXTURE, Broker
```
and replace every `Broker("localhost", 8080, managed=True)` with `Broker(FIXTURE, "localhost", 8080)` and every `Broker("127.0.0.1", free_port(), managed=True)` with `Broker(FIXTURE, "127.0.0.1", free_port())`.

In `TestMain` of the same file, the first test keeps its name and body (`BROKER=netmon.local:8080` still gives status 2 and names the address). In `TestBrokerContainer`, change the first test to expect `ensure(...)` to return `None` (delete `assert started is True` and the `started = ` assignment, calling `preview_broker.ensure(broker, Scan(2, 1, 2))` bare) and replace the second test with:

```python
    def test_refuses_a_broker_that_already_answers(self):
        broker = Broker(FIXTURE, "127.0.0.1", free_port())
        preview_broker.ensure(broker, Scan(1, 1, 1))
        try:
            with pytest.raises(RuntimeError, match="is taken"):
                preview_broker.ensure(broker, Scan(1, 1, 1))
        finally:
            preview_broker.stop()
```

In `tests/test_preview.py` replace `TestSettings` (it moves in Task 3; for now keep it green) with:

```python
@pytest.mark.tier0
class TestSettings:
    def test_defaults_to_the_fixture_the_standard_scan_and_safari(self):
        settings = preview.Settings.from_environ({})

        assert settings == preview.Settings(Scan(14, 39, 1), preview_broker.Broker(preview_broker.FIXTURE, "localhost", 8080), "Safari")

    def test_reads_all_three_variables(self):
        settings = preview.Settings.from_environ({"SCAN": "3+1x2", "BROKER": "netmon.local:8080", "INSPECT": "0"})

        assert settings == preview.Settings(Scan(3, 1, 2), preview_broker.Broker(preview_broker.EXTERNAL, "netmon.local", 8080), None)

    @pytest.mark.parametrize("environ, message", [({"SCAN": "x"}, "SCAN must be"), ({"BROKER": "x"}, "BROKER must be")])
    def test_names_the_malformed_variable(self, environ, message):
        with pytest.raises(ValueError, match=message):
            preview.Settings.from_environ(environ)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `uv run --frozen pytest tests/test_preview_broker.py tests/test_preview.py -m tier0 -q`
Expected: FAIL (`FIXTURE` is not defined in `preview_broker`).

- [ ] **Step 3: Implement**

In `tests/preview_broker.py`, delete `DEFAULT_BROKER`, add the kinds, and replace `Broker`, `parse_broker`, `ensure` and `main` with:

```python
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
```

```python
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
```

```python
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
```

In `tests/preview.py`, change `Settings.from_environ` to
```python
        return Settings(
            scan_fixtures.parse_scan(environ.get("SCAN") or "14+39"),
            preview_broker.parse_broker(environ.get("BROKER")),
            preview_kiosk.inspect_app(environ.get("INSPECT", "Safari")),
        )
```
and the broker lines of `run` to
```python
        if settings.broker.managed:
            preview_broker.ensure(settings.broker, settings.scan)
            cleanup.callback(preview_broker.stop)
            update(broker=True)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `uv run --frozen pytest tests/test_preview_broker.py tests/test_preview.py -m tier0 -q`
Expected: PASS.

Run: `make test-preview` (needs Podman; no Gradle is started by the broker tests).
Expected: `TestBrokerContainer` PASS.

- [ ] **Step 5: Commit**

```bash
git add tests/preview_broker.py tests/preview.py tests/test_preview_broker.py tests/test_preview.py
git commit -m "feat(preview): name the broker: fixture, device or HOST:PORT"
```

---

### Task 2: A dev server that does not reuse, and a stats proxy

**Files:**
- Modify: `tests/preview_dev_server.py` (`ensure`, `start`)
- Modify: `webpack.config.d/dev-server.js`
- Modify: `tests/preview.py` (the dev server lines of `run`)
- Test: `tests/test_preview_dev_server.py`

**Interfaces:**
- Consumes: Task 1 changes nothing here.
- Produces: `preview_dev_server.STATS_PROXY_ENV = "NETMON_STATS_PROXY"`; `ensure(stats_proxy: str | None = None) -> subprocess.Popen` (always the started process; raises `RuntimeError` when port 8081 answers); `start(stats_proxy: str | None = None) -> subprocess.Popen`.

- [ ] **Step 1: Write the failing tests**

Append to `tests/test_preview_dev_server.py`:

```python
class TestEnsure:
    def test_refuses_a_port_that_already_answers(self, monkeypatch):
        monkeypatch.setattr(preview_dev_server.preview_process, "answers", lambda host, port: True)

        with pytest.raises(RuntimeError, match="something already answers on port 8081; end it first"):
            preview_dev_server.ensure()


class TestStart:
    def test_gives_gradle_the_stats_proxy_in_its_environment(self, monkeypatch, tmp_path):
        launched = capture_popen(monkeypatch, tmp_path)

        preview_dev_server.start("http://netmon.local")

        assert launched["env"]["NETMON_STATS_PROXY"] == "http://netmon.local"

    def test_leaves_the_variable_out_without_a_proxy(self, monkeypatch, tmp_path):
        monkeypatch.delenv("NETMON_STATS_PROXY", raising=False)
        launched = capture_popen(monkeypatch, tmp_path)

        preview_dev_server.start()

        assert "NETMON_STATS_PROXY" not in launched["env"]


def capture_popen(monkeypatch, tmp_path):
    launched = {}
    monkeypatch.setattr(preview_dev_server, "LOG", tmp_path / "gradle.log")
    monkeypatch.setattr(preview_dev_server.subprocess, "Popen", lambda argv, **kwargs: launched.update(kwargs) or SimpleNamespace(pid=1))
    return launched
```

Append to `TestWebpackConfig`:

```python
    def test_proxies_the_stats_file_to_the_board_the_session_names(self):
        config = (preview_dev_server.ROOT / "webpack.config.d" / "dev-server.js").read_text()

        assert "process.env.NETMON_STATS_PROXY" in config
        assert "'/stats.json'" in config
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `uv run --frozen pytest tests/test_preview_dev_server.py -q`
Expected: FAIL (`ensure` returns None for a port that answers; `start` takes no argument).

- [ ] **Step 3: Implement**

In `tests/preview_dev_server.py` add `STATS_PROXY_ENV = "NETMON_STATS_PROXY"` below `LOG` and replace `ensure` and `start`:

```python
def ensure(stats_proxy: str | None = None) -> subprocess.Popen:
    """Starts the dev server and returns its process; raises RuntimeError when something already answers on its port."""
    if preview_process.answers("127.0.0.1", PORT):
        raise RuntimeError(f"something already answers on port {PORT}; end it first")
    process = start(stats_proxy)
    try:
        wait_until_serving(process)
    except BaseException:
        stop(process)
        raise
    return process


def start(stats_proxy: str | None = None) -> subprocess.Popen:
    LOG.parent.mkdir(parents=True, exist_ok=True)
    env = {**os.environ, **({STATS_PROXY_ENV: stats_proxy} if stats_proxy else {})}
    return subprocess.Popen(
        ["./gradlew", "--console=plain", "jsBrowserDevelopmentRun", "--continuous"],
        cwd=ROOT, env=env, stdout=LOG.open("w"), stderr=subprocess.STDOUT, start_new_session=True,
    )
```

Replace the module docstring's first line with `"""Gradle's dev server for the display, on the port the preview's pages and the kiosk use; the device flavor adds a proxy for the board's stats."""`.

In `webpack.config.d/dev-server.js` replace the file with:

```js
// noinspection JSUnresolvedReference

// The preview's pages and the VM kiosk reach the dev server on 8081, leaving 8080 to the broker. The kiosk asks for
// Host: 10.0.2.2:8081, which webpack-dev-server rejects unless allowedHosts says otherwise. With a host set, the client
// would reconnect to that host, which is the guest's own loopback in the VM, so it takes the address the page came from.
// The page asks for stats.json next to itself, which only a board's lighttpd serves: the device flavor names that board.
;(function (config) {
  'use strict'
  config.devServer = Object.assign(config.devServer || {}, {
    host: '127.0.0.1',
    port: 8081,
    allowedHosts: 'all',
    client: { webSocketURL: 'auto://0.0.0.0:0/ws' },
  })
  var statsProxy = process.env.NETMON_STATS_PROXY
  if (statsProxy) {
    config.devServer.proxy = [{ context: ['/stats.json'], target: statsProxy, changeOrigin: true }]
  }
})(config)
```

In `tests/preview.py` replace
```python
        server = preview_dev_server.ensure()
        if server:
            cleanup.callback(preview_dev_server.stop, server)
            update(gradle=server.pid)
```
with
```python
        server = preview_dev_server.ensure()
        cleanup.callback(preview_dev_server.stop, server)
        update(gradle=server.pid)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `uv run --frozen pytest tests/test_preview_dev_server.py tests/test_preview.py -m tier0 -q`
Expected: PASS.

- [ ] **Step 5: Check the proxy against a stand-in board (no hardware)**

No other Gradle may run. In one terminal:

```bash
mkdir -p dist/preview/standin && echo '{"at":1,"interval":5,"kioskCpu":42,"webCpu":40,"kioskMemory":1}' > dist/preview/standin/stats.json
python3 -m http.server 8099 --bind 127.0.0.1 --directory dist/preview/standin &
NETMON_STATS_PROXY=http://127.0.0.1:8099 ./gradlew --console=plain jsBrowserDevelopmentRun --continuous
```

In a second terminal, once Gradle prints that it serves on 8081:

```bash
curl -s http://127.0.0.1:8081/stats.json
```

Expected: the stand-in's JSON (`"kioskCpu":42`), not a 404. End Gradle with Ctrl-C and `kill %1` the stand-in. If the response is a 404, Gradle's webpack task does not see the variable or the dev server's proxy format differs: read `build/js/packages/*/webpack.config.js` (the generated file) and `build/js/node_modules/webpack-dev-server/package.json` for the version, fix `dev-server.js` accordingly, and update the spec's Open section with what was found.

- [ ] **Step 6: Commit**

```bash
git add tests/preview_dev_server.py tests/preview.py tests/test_preview_dev_server.py webpack.config.d/dev-server.js
git commit -m "feat(preview): fail on a taken dev server port, proxy a board's stats"
```

---

### Task 3: Settings with a flavor

**Files:**
- Create: `tests/preview_settings.py`
- Create: `tests/test_preview_settings.py`
- Modify: `tests/test_preview.py` (delete `TestSettings`)

**Interfaces:**
- Consumes: `preview_broker.parse_broker`, `preview_broker.DEVICE`, `preview_kiosk.inspect_app`, `scan_fixtures.parse_scan`.
- Produces: `preview_settings.FLAVORS = ("browser", "vm", "device")`; `Settings(flavor: str, scan: Scan, broker: Broker, inspect: str | None, target: str | None = None)` with `Settings.from_environ(flavor: str, environ) -> Settings`, raising `ValueError` with a one-line message.

- [ ] **Step 1: Write the failing tests**

Create `tests/test_preview_settings.py`:

```python
import pytest

import preview_broker
from preview_settings import Settings
from scan_fixtures import Scan

pytestmark = pytest.mark.tier0


class TestFromEnviron:
    def test_defaults_to_the_fixture_the_standard_scan_and_safari(self):
        settings = Settings.from_environ("vm", {})

        assert settings == Settings("vm", Scan(14, 39, 1), preview_broker.Broker(preview_broker.FIXTURE, "localhost", 8080), "Safari", None)

    def test_reads_every_variable_of_the_device_flavor(self):
        settings = Settings.from_environ("device", {"SCAN": "3+1x2", "BROKER": "device", "INSPECT": "0", "TARGET": "pi@netmon.local:2222"})

        assert settings == Settings("device", Scan(3, 1, 2), preview_broker.Broker(preview_broker.DEVICE, "127.0.0.1", 8080), None, "pi@netmon.local:2222")

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_takes_empty_variables_for_unset_ones(self, flavor):
        settings = Settings.from_environ(flavor, {"BROKER": "", "TARGET": "", "SCAN": ""})

        assert settings == Settings(flavor, Scan(14, 39, 1), preview_broker.Broker(preview_broker.FIXTURE, "localhost", 8080), "Safari", None)

    @pytest.mark.parametrize("environ, message", [({"SCAN": "x"}, "SCAN must be"), ({"BROKER": "x"}, "BROKER must be fixture, device or HOST:PORT")])
    def test_names_the_malformed_variable(self, environ, message):
        with pytest.raises(ValueError, match=message):
            Settings.from_environ("vm", environ)

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_keeps_the_boards_broker_for_the_device(self, flavor):
        with pytest.raises(ValueError, match="BROKER=device is only for preview-device"):
            Settings.from_environ(flavor, {"BROKER": "device"})

    def test_needs_a_target_for_the_device(self):
        with pytest.raises(ValueError, match="preview-device needs TARGET=user@host"):
            Settings.from_environ("device", {})

    @pytest.mark.parametrize("flavor", ["browser", "vm"])
    def test_refuses_a_target_elsewhere(self, flavor):
        with pytest.raises(ValueError, match="TARGET is only for preview-device"):
            Settings.from_environ(flavor, {"TARGET": "pi@netmon.local"})

    def test_refuses_an_unknown_flavor(self):
        with pytest.raises(ValueError, match="flavor must be browser, vm or device"):
            Settings.from_environ("tv", {})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `uv run --frozen pytest tests/test_preview_settings.py -q`
Expected: FAIL (`No module named 'preview_settings'`).

- [ ] **Step 3: Implement**

Create `tests/preview_settings.py`:

```python
"""The preview's settings: its flavor and the variables every flavor takes."""
from dataclasses import dataclass

import preview_broker
import preview_kiosk
import scan_fixtures

FLAVORS = ("browser", "vm", "device")


@dataclass(frozen=True)
class Settings:
    flavor: str
    scan: scan_fixtures.Scan
    broker: preview_broker.Broker
    inspect: str | None
    target: str | None = None

    @staticmethod
    def from_environ(flavor: str, environ) -> "Settings":
        if flavor not in FLAVORS:
            raise ValueError(f"flavor must be browser, vm or device, not {flavor!r}")
        scan = scan_fixtures.parse_scan(environ.get("SCAN") or "14+39")
        broker = preview_broker.parse_broker(environ.get("BROKER"))
        target = environ.get("TARGET") or None
        if broker.kind == preview_broker.DEVICE and flavor != "device":
            raise ValueError("BROKER=device is only for preview-device")
        if flavor == "device" and not target:
            raise ValueError("preview-device needs TARGET=user@host")
        if flavor != "device" and target:
            raise ValueError("TARGET is only for preview-device")
        return Settings(flavor, scan, broker, preview_kiosk.inspect_app(environ.get("INSPECT", "Safari")), target)
```

In `tests/test_preview.py` delete `TestSettings` and the imports it alone used (`Scan` from `scan_fixtures`, and `preview_broker` if nothing else in the file uses it).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `uv run --frozen pytest tests/test_preview_settings.py -q`
Expected: PASS. (`tests/preview.py` still has its own `Settings` until Task 4; `tests/test_preview.py` no longer touches it.)

- [ ] **Step 5: Commit**

```bash
git add tests/preview_settings.py tests/test_preview_settings.py tests/test_preview.py
git commit -m "feat(preview): add the flavor and TARGET to the preview's settings"
```

---

### Task 4: Browser and VM flavors, the `--on` option, the make targets

**Files:**
- Create: `tests/preview_flavors.py`
- Create: `tests/test_preview_flavors.py`
- Create: `tests/test_makefile.py`
- Modify: `tests/preview_kiosk.py` (add `INSPECTOR_PORT`, `browser_url`)
- Modify: `tests/preview.py` (drop `Settings`, flavor-driven `run`, `main` with `--on`)
- Modify: `tests/test_preview.py`, `tests/test_preview_kiosk.py`
- Modify: `Makefile`

**Interfaces:**
- Consumes: `Settings` from Task 3; `preview_broker.Broker`; `preview_dev_server.ensure(stats_proxy)`.
- Produces: `preview_kiosk.INSPECTOR_PORT = 2999`, `preview_kiosk.browser_url(dev_port, broker_host, broker_port) -> str`; `preview_flavors.Shown(page: str, inspector: str | None = None)`; flavor classes `Browser`, `Vm(session_dir: Path)`, each with `show(cleanup: ExitStack, settings, update) -> Shown` and `stats_origin(settings) -> str | None`; `preview_flavors.flavor_for(settings, session_dir) -> Flavor`; `preview.main(argv=None, environ=os.environ) -> int`.

- [ ] **Step 1: Write the failing tests**

Append to `tests/test_preview_kiosk.py`:

```python
class TestBrowserUrl:
    def test_names_the_dev_server_and_the_broker_as_the_macs_browser_reaches_them(self):
        url = preview_kiosk.browser_url(8081, "localhost", 8080)

        assert url == "http://localhost:8081/?broker.host=localhost&broker.port=8080"

    def test_passes_a_remote_broker_unchanged(self):
        url = preview_kiosk.browser_url(8081, "netmon.local", 8080)

        assert url == "http://localhost:8081/?broker.host=netmon.local&broker.port=8080"


class TestInspectorPort:
    def test_is_2999(self):
        assert preview_kiosk.INSPECTOR_PORT == 2999
```

Create `tests/test_preview_flavors.py`:

```python
from contextlib import ExitStack
from pathlib import Path

import pytest

import preview_flavors
from preview_settings import Settings

pytestmark = pytest.mark.tier0


class TestBrowser:
    def test_shows_the_page_with_its_broker_and_has_no_inspector_of_its_own(self):
        settings = Settings.from_environ("browser", {})

        with ExitStack() as cleanup:
            shown = preview_flavors.Browser().show(cleanup, settings, fail_on_update)

        assert shown == preview_flavors.Shown("http://localhost:8081/?broker.host=localhost&broker.port=8080")

    def test_takes_a_remote_broker_as_it_is(self):
        settings = Settings.from_environ("browser", {"BROKER": "netmon.local:8080"})

        with ExitStack() as cleanup:
            shown = preview_flavors.Browser().show(cleanup, settings, fail_on_update)

        assert shown.page == "http://localhost:8081/?broker.host=netmon.local&broker.port=8080"

    def test_proxies_no_stats(self):
        assert preview_flavors.Browser().stats_origin(Settings.from_environ("browser", {})) is None


class TestFlavorFor:
    @pytest.mark.parametrize("flavor, cls", [("browser", preview_flavors.Browser), ("vm", preview_flavors.Vm)])
    def test_picks_the_class_of_the_flavor(self, flavor, cls):
        picked = preview_flavors.flavor_for(Settings.from_environ(flavor, {}), Path("session"))

        assert isinstance(picked, cls)

    def test_proxies_no_stats_in_the_vm(self):
        assert preview_flavors.Vm(Path("session")).stats_origin(Settings.from_environ("vm", {})) is None


def fail_on_update(**fields):
    raise AssertionError(f"unexpected update {fields}")
```

Create `tests/test_makefile.py`:

```python
import subprocess

import pytest

pytestmark = pytest.mark.tier0


class TestPreviewTargets:
    @pytest.mark.parametrize("target, flavor", [("preview-browser", "browser"), ("preview-vm", "vm"), ("preview", "vm"), ("preview-device", "device")])
    def test_runs_the_preview_in_its_flavor(self, target, flavor):
        result = subprocess.run(["make", "-n", target, "TARGET=pi@netmon.local"], capture_output=True, text=True, check=False)

        assert result.returncode == 0, result.stderr
        assert f"tests/preview.py --on {flavor}" in result.stdout

    def test_lists_the_targets_in_help(self):
        result = subprocess.run(["make", "help"], capture_output=True, text=True, check=False)

        assert [name for name in ("preview-browser", "preview-vm", "preview-device") if name in result.stdout] == ["preview-browser", "preview-vm", "preview-device"]
```

In `tests/test_preview.py` add:

```python
@pytest.mark.tier0
class TestMain:
    def test_names_a_variable_that_does_not_fit_the_flavor_and_runs_nothing(self, capsys):
        status = preview.main(["--on", "device"], {})

        assert status == 2
        assert "preview-device needs TARGET=user@host" in capsys.readouterr().err

    def test_refuses_an_unknown_flavor(self):
        with pytest.raises(SystemExit) as exit_:
            preview.main(["--on", "tv"], {})

        assert exit_.value.code == 2
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `uv run --frozen pytest tests/test_preview_kiosk.py tests/test_preview_flavors.py tests/test_makefile.py tests/test_preview.py -m tier0 -q`
Expected: FAIL (no `browser_url`, no `preview_flavors`, no `preview-browser` target, `main` takes no argv).

- [ ] **Step 3: Implement the kiosk helpers and the flavors**

In `tests/preview_kiosk.py` add below `LOOPBACK`:

```python
INSPECTOR_PORT = 2999
```
and below `page_url`:

```python
def browser_url(dev_port: int, broker_host: str, broker_port: int) -> str:
    return f"http://localhost:{dev_port}/?broker.host={broker_host}&broker.port={broker_port}"
```

Create `tests/preview_flavors.py`:

```python
"""The places a preview shows the page: a browser tab, the kiosk in a VM window and, below, the kiosk of a real board."""
from contextlib import ExitStack
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Protocol

import preview_dev_server
import preview_device
import preview_kiosk
import preview_session
from preview_settings import Settings

DEV_PORT = preview_dev_server.PORT


@dataclass(frozen=True)
class Shown:
    """What a flavor put up: the page's URL as the Mac's browser loads it, and the kiosk's Web Inspector address, None where the page's own tools inspect it."""

    page: str
    inspector: str | None = None


class Flavor(Protocol):
    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown: ...

    def stats_origin(self, settings: Settings) -> str | None: ...


class Browser:
    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown:
        return Shown(preview_kiosk.browser_url(DEV_PORT, settings.broker.host, settings.broker.port))

    def stats_origin(self, settings: Settings) -> str | None:
        return None


class Vm:
    def __init__(self, session_dir: Path):
        self.session_dir = session_dir

    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown:
        layer = preview_device.ensure_layer()
        session = preview_session.Session(layer, self.session_dir)
        cleanup.callback(session.stop)
        session.start(on_qemu=lambda pid: update(qemu=pid))
        session.place_window()
        session.configure_kiosk(preview_kiosk.page_url(DEV_PORT, settings.broker.host, settings.broker.port), preview_kiosk.INSPECTOR_PORT)
        session.open_tunnel(preview_kiosk.INSPECTOR_PORT, preview_kiosk.INSPECTOR_PORT)
        return Shown(f"http://localhost:{DEV_PORT}/", f"127.0.0.1:{preview_kiosk.INSPECTOR_PORT}")

    def stats_origin(self, settings: Settings) -> str | None:
        return None


def flavor_for(settings: Settings, session_dir: Path) -> Flavor:
    if settings.flavor == "browser":
        return Browser()
    if settings.flavor == "vm":
        return Vm(session_dir)
    raise ValueError(f"no flavor {settings.flavor!r} yet")
```

(The last line is replaced in Task 6 when the device flavor exists.)

- [ ] **Step 4: Implement the orchestration**

In `tests/preview.py`:

1. Replace the module docstring with `"""make preview-browser, preview-vm and preview-device: the broker, the dev server and the page in a browser, a VM's kiosk or a board's kiosk, until Ctrl-C."""`.
2. Replace the import block (everything from `import json` up to the `ROOT = …` line) with:

```python
import argparse
import json
import os
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
from contextlib import ExitStack
from pathlib import Path
from typing import Callable

import preview_broker
import preview_dev_server
import preview_flavors
import preview_kiosk
import preview_process
import preview_settings
```
3. Delete `INSPECTOR_PORT = 2999`, the `Settings` dataclass, and the old `run` and `main`. Add, in their place at the end of the file (before `if __name__`):

```python
def run(settings: preview_settings.Settings, flavor: preview_flavors.Flavor) -> int:
    preview_process.raise_on_sigterm()
    claim()
    with ExitStack() as cleanup:
        cleanup.callback(RECORD.unlink, missing_ok=True)
        if settings.broker.managed:
            preview_broker.ensure(settings.broker, settings.scan)
            cleanup.callback(preview_broker.stop)
            update(broker=True)
        print(f"dev server: Gradle on port {DEV_PORT}, log in {preview_dev_server.LOG}", file=sys.stderr, flush=True)
        server = preview_dev_server.ensure(flavor.stats_origin(settings))
        cleanup.callback(preview_dev_server.stop, server)
        update(gradle=server.pid)
        shown = flavor.show(cleanup, settings, update)
        opened = wait_for_inspector(shown.inspector) if shown.inspector else shown.page
        if settings.inspect:
            subprocess.run(preview_kiosk.open_command(settings.inspect, opened), check=False)
        inspector = f"\n  inspector  http://{shown.inspector}/" if shown.inspector else ""
        ready = f"preview ready ({settings.flavor})\n  page       {shown.page}\n  broker     {settings.broker.describe()}{inspector}"
        print(f"{ready}\nCtrl-C ends it.", file=sys.stderr, flush=True)
        preview_process.until_interrupted()
    return 0


def main(argv: list[str] | None = None, environ=os.environ) -> int:
    parser = argparse.ArgumentParser(prog="preview.py", description=__doc__)
    parser.add_argument("--on", required=True, choices=preview_settings.FLAVORS, help="where to show the page")
    flavor_name = parser.parse_args(argv).on
    try:
        settings = preview_settings.Settings.from_environ(flavor_name, environ)
        return run(settings, preview_flavors.flavor_for(settings, SESSION_DIR))
    except (ValueError, AlreadyRunning, RuntimeError, TimeoutError) as error:
        print(error, file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
```

(The old `run` and `main` are removed first; `wait_for_inspector`, `claim`, `update`, `stale_actions`, `carry_out`, `wait_until_gone` stay as they are.)

- [ ] **Step 5: Implement the make targets**

In `Makefile`: add `preview-browser preview-vm preview-device` to the `.PHONY` list, and replace the `preview:` target with:

```make
preview-browser: ## the broker, the dev server and the page in a browser tab (BROKER=fixture|HOST:PORT SCAN=14+39 INSPECT=Safari)
	@$(UV) python tests/preview.py --on browser

preview-vm: ## the broker, the dev server and the kiosk's WebKit in a VM window, its inspector in Safari (BROKER=fixture|HOST:PORT SCAN=14+39 INSPECT=Safari)
	@$(UV) python tests/preview.py --on vm

preview-device: ## the broker, the dev server and the kiosk of a real Pi, its inspector in Safari (TARGET=pi@host BROKER=fixture|device|HOST:PORT SCAN=14+39 INSPECT=Safari)
	@$(UV) python tests/preview.py --on device

preview: preview-vm ## the same as preview-vm
```

Also change the help text of `broker:` to `run the preview's Mosquitto with the fixture until Ctrl-C (SCAN=14+39 sets the hosts)`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `uv run --frozen pytest tests/test_preview_kiosk.py tests/test_preview_flavors.py tests/test_makefile.py tests/test_preview.py tests/test_preview_settings.py -m tier0 -q`
Expected: PASS.

- [ ] **Step 7: Check both flavors for real**

No other Gradle may run.

```bash
make preview-browser INSPECT=0
```
Expected on stderr within a minute or two: `preview ready (browser)` with `page http://localhost:8081/?broker.host=localhost&broker.port=8080` and `broker fixture on localhost:8080`; no `inspector` line. In another terminal: `curl -s 'http://localhost:8081/' | head -3` shows the page; `podman ps --format '{{.Names}}'` lists `netmon-preview-broker`; a second `make preview-vm` fails with `a preview is already running (process N)`. Ctrl-C in the first terminal; then `podman ps` and `pgrep -fl gradle` show nothing of the preview's.

```bash
make preview-vm
```
Expected: the VM window, Safari opens the Web Inspector, `preview ready (vm)` with the inspector line; Ctrl-C leaves no `qemu-system` and no container. Then `make test-preview` PASS.

- [ ] **Step 8: Commit**

```bash
git add tests/preview.py tests/preview_flavors.py tests/preview_kiosk.py tests/test_preview.py tests/test_preview_flavors.py tests/test_preview_kiosk.py tests/test_makefile.py Makefile
git commit -m "feat(preview): add preview-browser and preview-vm over one flavor interface"
```

---

### Task 5: The board: its files, its tunnel and its ssh

**Files:**
- Create: `tests/preview_board.py`
- Create: `tests/test_preview_board.py`

**Interfaces:**
- Consumes: `preview_kiosk.session_kiosk_conf(current, url, inspector_port)`, `preview_kiosk.LOOPBACK`, `preview_kiosk.INSPECTOR_PORT`, `pihero_testkit.ssh.command(uri, remote)`, `pihero_testkit.ssh.KEEPALIVE`, `Broker` from Task 1.
- Produces: `host_of(target) -> str`, `stats_origin(target) -> str`, `on_the_mac(broker) -> bool`, `page_url(broker) -> str`, `forwards(broker, dev_port, inspector_port) -> list[str]`, `tunnel_command(target, forwards) -> list[str]`, and `Board(target, run=subprocess.run)` with `check_kiosk()`, `install(broker, timeout=90, sleep=time.sleep, clock=time.monotonic)`, `restore() -> bool`, `open_tunnel(broker, dev_port, inspector_port) -> subprocess.Popen`, `close_tunnel(tunnel)`. Constants `DEV_REMOTE_PORT = 18081`, `BROKER_REMOTE_PORT = 18080`.

- [ ] **Step 1: Write the failing tests**

Create `tests/test_preview_board.py`:

```python
import subprocess

import pytest
from pihero_testkit import ssh

import preview_board
import preview_kiosk
from preview_broker import DEVICE, EXTERNAL, FIXTURE, Broker

pytestmark = pytest.mark.tier0
TARGET = "pi@netmon.local"
FIXTURE_BROKER = Broker(FIXTURE, "localhost", 8080)
DEVICE_BROKER = Broker(DEVICE, "127.0.0.1", 8080)
SAMPLE_CONF = """\
URL=http://localhost/?broker.host=localhost&broker.port=8080
COG_ARGS="--doc-viewer --web-mem-limit=200"
JSC_useJIT=false
"""


class TestHostOf:
    @pytest.mark.parametrize("target", ["pi@netmon.local", "pi@netmon.local:2222", "netmon.local"])
    def test_is_the_host_of_user_host_and_port(self, target):
        assert preview_board.host_of(target) == "netmon.local"


class TestStatsOrigin:
    def test_is_the_boards_web_server_whatever_the_ssh_port(self):
        assert preview_board.stats_origin("pi@netmon.local:2222") == "http://netmon.local"


class TestOnTheMac:
    @pytest.mark.parametrize("broker, expected", [
        (FIXTURE_BROKER, True),
        (Broker(EXTERNAL, "localhost", 9000), True),
        (Broker(EXTERNAL, "127.0.0.1", 9000), True),
        (Broker(EXTERNAL, "::1", 9000), True),
        (Broker(EXTERNAL, "netmon.local", 9000), False),
        (DEVICE_BROKER, False),
    ])
    def test_is_true_for_the_fixture_and_a_loopback_host_port(self, broker, expected):
        assert preview_board.on_the_mac(broker) is expected


class TestPageUrl:
    def test_reaches_a_broker_on_the_mac_through_the_reverse_port(self):
        url = preview_board.page_url(Broker(EXTERNAL, "localhost", 9000))

        assert url == "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080"

    def test_reaches_the_boards_own_broker_on_its_loopback(self):
        assert preview_board.page_url(DEVICE_BROKER) == "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=8080"

    def test_passes_a_remote_broker_unchanged(self):
        url = preview_board.page_url(Broker(EXTERNAL, "netmon.local", 9000))

        assert url == "http://127.0.0.1:18081/?broker.host=netmon.local&broker.port=9000"


class TestForwards:
    def test_forwards_the_dev_server_the_inspector_and_a_broker_on_the_mac(self):
        args = preview_board.forwards(Broker(EXTERNAL, "localhost", 9000), 8081, 2999)

        assert args == ["-R", "127.0.0.1:18081:127.0.0.1:8081", "-L", "127.0.0.1:2999:127.0.0.1:2999", "-R", "127.0.0.1:18080:127.0.0.1:9000"]

    @pytest.mark.parametrize("broker", [DEVICE_BROKER, Broker(EXTERNAL, "netmon.local", 9000)])
    def test_forwards_no_broker_that_is_not_on_the_mac(self, broker):
        args = preview_board.forwards(broker, 8081, 2999)

        assert args == ["-R", "127.0.0.1:18081:127.0.0.1:8081", "-L", "127.0.0.1:2999:127.0.0.1:2999"]


class TestTunnelCommand:
    def test_holds_the_forwards_open_without_a_command_and_fails_on_a_forward_that_cannot_be_made(self):
        command = preview_board.tunnel_command("pi@netmon.local:2222", ["-R", "a"])

        assert command == [
            "ssh", "-N", "-o", "BatchMode=yes", "-o", "ExitOnForwardFailure=yes", "-o", "ConnectTimeout=10",
            *ssh.KEEPALIVE, "-p", "2222", "-R", "a", "pi@netmon.local",
        ]

    def test_leaves_the_port_out_without_one(self):
        command = preview_board.tunnel_command(TARGET, [])

        assert "-p" not in command
        assert command[-1] == TARGET


class TestCheckKiosk:
    def test_passes_a_board_with_the_kiosk(self):
        preview_board.Board(TARGET, run=Script({})).check_kiosk()

    def test_names_an_unreachable_board(self):
        board = preview_board.Board(TARGET, run=Script({"dpkg-query": (255, "", "ssh: connect to host netmon.local port 22: Connection refused")}))

        with pytest.raises(RuntimeError, match="cannot reach pi@netmon.local over ssh: .*Connection refused"):
            board.check_kiosk()

    def test_names_a_board_without_the_kiosk(self):
        board = preview_board.Board(TARGET, run=Script({"dpkg-query": (1, "", "no packages found")}))

        with pytest.raises(RuntimeError, match="pi@netmon.local has no pihero-kiosk"):
            board.check_kiosk()


class TestInstall:
    def test_writes_the_session_conf_and_the_drop_in_in_one_command_and_waits_for_the_page(self):
        script = Script(replies())
        board = preview_board.Board(TARGET, run=script)

        board.install(FIXTURE_BROKER, sleep=lambda s: None)

        sent = [(remote, stdin) for remote, stdin in script.calls if stdin]
        expected = preview_kiosk.session_kiosk_conf(SAMPLE_CONF, "http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080", 2999)
        assert [stdin for _, stdin in sent] == [expected]
        remote = sent[0][0]
        assert "/run/netmon-preview/kiosk.conf" in remote
        assert "/run/systemd/system/pihero-kiosk.service.d/preview.conf" in remote
        assert "EnvironmentFile=/run/netmon-preview/kiosk.conf" in remote
        assert "daemon-reload" in remote and "restart pihero-kiosk" in remote

    def test_changes_nothing_on_a_kiosk_conf_it_cannot_use(self):
        script = Script(replies(conf="URL=http://localhost/\n"))
        board = preview_board.Board(TARGET, run=script)

        with pytest.raises(ValueError, match="kiosk.conf"):
            board.install(FIXTURE_BROKER, sleep=lambda s: None)

        assert [stdin for _, stdin in script.calls if stdin] == []

    def test_reports_the_command_that_failed(self):
        board = preview_board.Board(TARGET, run=Script(replies(install=(1, "", "sudo: a password is required"))))

        with pytest.raises(RuntimeError, match="a password is required"):
            board.install(FIXTURE_BROKER, sleep=lambda s: None)

    def test_gives_up_on_a_page_the_kiosk_never_loads(self):
        now = iter(range(0, 1000, 10))
        board = preview_board.Board(TARGET, run=Script(replies(loaded="0\n")))

        with pytest.raises(TimeoutError, match="did not load"):
            board.install(FIXTURE_BROKER, timeout=30, sleep=lambda s: None, clock=lambda: next(now))


class TestRestore:
    def test_removes_the_session_files_and_restarts_the_kiosk(self):
        script = Script({})

        restored = preview_board.Board(TARGET, run=script).restore()

        remote = script.calls[0][0]
        assert restored is True
        assert "rm -rf /run/netmon-preview /run/systemd/system/pihero-kiosk.service.d/preview.conf" in remote
        assert "daemon-reload" in remote and "restart pihero-kiosk" in remote

    def test_warns_and_says_a_reboot_helps_on_a_board_that_does_not_answer(self, capsys):
        restored = preview_board.Board(TARGET, run=Script({"rm -rf": (255, "", "Connection timed out")})).restore()

        assert restored is False
        assert "a reboot of the board removes the session's files" in capsys.readouterr().err

    def test_warns_on_a_command_that_hangs(self, capsys):
        def hangs(argv, **kwargs):
            raise subprocess.TimeoutExpired(argv, 30)

        restored = preview_board.Board(TARGET, run=hangs).restore()

        assert restored is False
        assert "could not restore the kiosk on pi@netmon.local" in capsys.readouterr().err


class Script:
    def __init__(self, replies):
        self.replies, self.calls = replies, []

    def __call__(self, argv, input=None, **kwargs):
        remote = argv[-1]
        self.calls.append((remote, input))
        for needle, (code, out, err) in self.replies.items():
            if needle in remote:
                return subprocess.CompletedProcess(argv, code, out, err)
        return subprocess.CompletedProcess(argv, 0, "", "")


def replies(conf=SAMPLE_CONF, install=(0, "", ""), loaded="1\n"):
    return {
        "cat /etc/pihero/kiosk.conf": (0, conf, ""),
        "date '+": (0, "2026-10-03 10:00:00\n", ""),
        "tee /run/netmon-preview/kiosk.conf": install,
        "grep -c": (0, loaded, ""),
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `uv run --frozen pytest tests/test_preview_board.py -q`
Expected: FAIL (`No module named 'preview_board'`).

- [ ] **Step 3: Implement**

Create `tests/preview_board.py`:

```python
"""The preview's real board: the page's address on it, the ssh tunnel to the Mac, and the session files the kiosk reads from /run."""
import subprocess
import sys
import time

from pihero_testkit import ssh

import preview_broker
import preview_kiosk

DEV_REMOTE_PORT = 18081
BROKER_REMOTE_PORT = 18080
RUN_DIR = "/run/netmon-preview"
CONF = f"{RUN_DIR}/kiosk.conf"
DROPIN_DIR = "/run/systemd/system/pihero-kiosk.service.d"
DROPIN = f"{DROPIN_DIR}/preview.conf"
INSTALL = (
    f"sudo install -d {RUN_DIR} {DROPIN_DIR} && sudo tee {CONF} >/dev/null && "
    f"printf '[Service]\\nEnvironmentFile={CONF}\\n' | sudo tee {DROPIN} >/dev/null && "
    "sudo systemctl daemon-reload && sudo systemctl restart pihero-kiosk"
)
RESTORE = (
    f"sudo rm -rf {RUN_DIR} {DROPIN}; sudo rmdir --ignore-fail-on-non-empty {DROPIN_DIR} 2>/dev/null; "
    "sudo systemctl daemon-reload && sudo systemctl restart pihero-kiosk"
)


def host_of(target: str) -> str:
    """Returns the host of user@host[:port]."""
    return target.partition(":")[0].rpartition("@")[2]


def stats_origin(target: str) -> str:
    return f"http://{host_of(target)}"


def on_the_mac(broker: preview_broker.Broker) -> bool:
    """Returns whether the broker runs on the Mac: the fixture, or a HOST:PORT with a loopback host."""
    return broker.kind == preview_broker.FIXTURE or (broker.kind == preview_broker.EXTERNAL and broker.host in preview_kiosk.LOOPBACK)


def page_url(broker: preview_broker.Broker) -> str:
    host, port = ("127.0.0.1", BROKER_REMOTE_PORT) if on_the_mac(broker) else (broker.host, broker.port)
    return f"http://127.0.0.1:{DEV_REMOTE_PORT}/?broker.host={host}&broker.port={port}"


def forwards(broker: preview_broker.Broker, dev_port: int, inspector_port: int) -> list[str]:
    args = ["-R", f"127.0.0.1:{DEV_REMOTE_PORT}:127.0.0.1:{dev_port}", "-L", f"127.0.0.1:{inspector_port}:127.0.0.1:{inspector_port}"]
    if on_the_mac(broker):
        args += ["-R", f"127.0.0.1:{BROKER_REMOTE_PORT}:127.0.0.1:{broker.port}"]
    return args


def tunnel_command(target: str, forward_args: list[str]) -> list[str]:
    user_host, _, port = target.partition(":")
    return [
        "ssh", "-N", "-o", "BatchMode=yes", "-o", "ExitOnForwardFailure=yes", "-o", "ConnectTimeout=10",
        *ssh.KEEPALIVE, *(["-p", port] if port else []), *forward_args, user_host,
    ]


class Board:
    def __init__(self, target: str, run=subprocess.run):
        self.target, self._run = target, run

    def ssh(self, remote: str, input: str | None = None, timeout: float = 60) -> subprocess.CompletedProcess:
        return self._run(ssh.command(self.target, remote), input=input, text=True, capture_output=True, check=False, timeout=timeout)

    def check_kiosk(self) -> None:
        result = self.ssh("dpkg-query -W pihero-kiosk")
        if result.returncode == 255:
            raise RuntimeError(f"cannot reach {self.target} over ssh: {result.stderr.strip()}")
        if result.returncode != 0:
            raise RuntimeError(f"{self.target} has no pihero-kiosk; flash a Pi Hero device file first")

    def install(self, broker: preview_broker.Broker, timeout: float = 90, sleep=time.sleep, clock=time.monotonic) -> None:
        """Points the board's kiosk at the dev server through the tunnel, with the inspector on; raises ValueError for a kiosk.conf it cannot change."""
        current = self.ssh("cat /etc/pihero/kiosk.conf")
        if current.returncode != 0:
            raise RuntimeError(f"cannot read /etc/pihero/kiosk.conf on {self.target}: {current.stderr.strip()}")
        conf = preview_kiosk.session_kiosk_conf(current.stdout, page_url(broker), preview_kiosk.INSPECTOR_PORT)
        since = self.ssh("date '+%Y-%m-%d %H:%M:%S'").stdout.strip()
        result = self.ssh(INSTALL, input=conf)
        if result.returncode != 0:
            raise RuntimeError(f"could not put the session on {self.target}: {result.stderr.strip()}")
        self.wait_loaded(since, timeout, sleep, clock)

    def wait_loaded(self, since: str, timeout: float, sleep, clock) -> None:
        deadline = clock() + timeout
        while clock() < deadline:
            loaded = self.ssh(f"sudo journalctl -u pihero-kiosk --since '{since}' --no-pager | grep -c 'Loaded successfully'").stdout.strip()
            if loaded not in ("", "0"):
                return
            sleep(1)
        raise TimeoutError(f"the kiosk on {self.target} did not load its page within {timeout:g} s; is the dev server up and the tunnel open?")

    def restore(self) -> bool:
        """Removes the session's files and restarts the kiosk; returns whether the board answered, else warns."""
        try:
            result = self.ssh(RESTORE, timeout=30)
        except (subprocess.TimeoutExpired, OSError) as error:
            print(f"could not restore the kiosk on {self.target}: {error}; a reboot of the board removes the session's files", file=sys.stderr)
            return False
        if result.returncode != 0:
            print(f"could not restore the kiosk on {self.target}: {result.stderr.strip()}; a reboot of the board removes the session's files", file=sys.stderr)
        return result.returncode == 0

    def open_tunnel(self, broker: preview_broker.Broker, dev_port: int, inspector_port: int) -> subprocess.Popen:
        tunnel = subprocess.Popen(tunnel_command(self.target, forwards(broker, dev_port, inspector_port)), stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if tunnel.poll() is not None:
                raise RuntimeError(f"the ssh tunnel to {self.target} ended: {tunnel.stderr.read().strip()}")
            if preview_process.answers("127.0.0.1", inspector_port):
                return tunnel
            time.sleep(0.25)
        self.close_tunnel(tunnel)
        raise TimeoutError(f"the ssh tunnel to {self.target} did not come up within 15 s")

    def close_tunnel(self, tunnel: subprocess.Popen) -> None:
        if tunnel.poll() is None:
            tunnel.terminate()
            try:
                tunnel.wait(5)
            except subprocess.TimeoutExpired:
                tunnel.kill()
```

and add `import preview_process` next to `import preview_kiosk` at the top.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `uv run --frozen pytest tests/test_preview_board.py -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tests/preview_board.py tests/test_preview_board.py
git commit -m "feat(preview): add the board's session files, tunnel and ssh commands"
```

---

### Task 6: The device flavor and the recovery of a killed session

**Files:**
- Modify: `tests/preview_flavors.py` (add `Device`, update `flavor_for`)
- Modify: `tests/preview.py` (`stale_actions`, `carry_out`, import `preview_board`)
- Test: `tests/test_preview_flavors.py`, `tests/test_preview.py`

**Interfaces:**
- Consumes: `Board` and the helpers of Task 5; `Shown`, `Flavor` of Task 4.
- Produces: `preview_flavors.Device` (`show`, `stats_origin`); records `device` and `tunnel`; `stale_actions` returning `("terminate", tunnel_pid)` and `("restore-device", target)`.

- [ ] **Step 1: Write the failing tests**

Append to `tests/test_preview_flavors.py` (imports at the top: `from types import SimpleNamespace`, `import preview_board`):

```python
class TestDevice:
    def test_puts_the_session_on_the_board_in_order_and_records_what_a_crash_would_leave(self, monkeypatch):
        calls, updates = [], []
        install_board(monkeypatch, calls)
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with ExitStack() as cleanup:
            shown = preview_flavors.Device().show(cleanup, settings, lambda **fields: updates.append(fields))

        assert shown == preview_flavors.Shown("http://localhost:8081/", "127.0.0.1:2999")
        assert calls == ["check_kiosk", "open_tunnel", "install", "close_tunnel", "restore"]
        assert updates == [{"device": "pi@netmon.local"}, {"tunnel": 77}]

    def test_restores_the_board_and_closes_the_tunnel_when_the_install_fails(self, monkeypatch):
        calls = []
        install_board(monkeypatch, calls, install_error=RuntimeError("could not put the session on pi@netmon.local"))
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with pytest.raises(RuntimeError, match="could not put the session"):
            with ExitStack() as cleanup:
                preview_flavors.Device().show(cleanup, settings, lambda **fields: None)

        assert calls == ["check_kiosk", "open_tunnel", "install", "close_tunnel", "restore"]

    def test_touches_nothing_on_a_board_without_the_kiosk(self, monkeypatch):
        calls = []
        install_board(monkeypatch, calls, check_error=RuntimeError("pi@netmon.local has no pihero-kiosk"))
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local"})

        with pytest.raises(RuntimeError, match="no pihero-kiosk"):
            with ExitStack() as cleanup:
                preview_flavors.Device().show(cleanup, settings, lambda **fields: None)

        assert calls == ["check_kiosk"]

    def test_names_the_boards_web_server_for_the_stats(self):
        settings = Settings.from_environ("device", {"TARGET": "pi@netmon.local:2222"})

        assert preview_flavors.Device().stats_origin(settings) == "http://netmon.local"

    def test_is_picked_for_the_device_flavor(self):
        picked = preview_flavors.flavor_for(Settings.from_environ("device", {"TARGET": "pi@netmon.local"}), Path("session"))

        assert isinstance(picked, preview_flavors.Device)


def install_board(monkeypatch, calls, install_error=None, check_error=None):
    class FakeBoard:
        def __init__(self, target):
            self.target = target

        def check_kiosk(self):
            calls.append("check_kiosk")
            if check_error:
                raise check_error

        def open_tunnel(self, broker, dev_port, inspector_port):
            calls.append("open_tunnel")
            return SimpleNamespace(pid=77)

        def install(self, broker):
            calls.append("install")
            if install_error:
                raise install_error

        def close_tunnel(self, tunnel):
            calls.append("close_tunnel")

        def restore(self):
            calls.append("restore")

    monkeypatch.setattr(preview_flavors.preview_board, "Board", FakeBoard)
```

Append to `TestStaleActions` in `tests/test_preview.py`:

```python
    def test_ends_a_killed_previews_tunnel_and_restores_its_board(self):
        record = {"owner": 100, "tunnel": 400, "device": "pi@netmon.local"}

        actions = preview.stale_actions(record, commands({400: "ssh -N -o BatchMode=yes pi@netmon.local"}))

        assert actions == [("terminate", 400), ("restore-device", "pi@netmon.local")]

    def test_restores_a_board_whose_tunnel_is_already_gone(self):
        record = {"owner": 100, "tunnel": 400, "device": "pi@netmon.local"}

        actions = preview.stale_actions(record, commands({}))

        assert actions == [("restore-device", "pi@netmon.local")]
```

and a new class:

```python
@pytest.mark.tier0
class TestCarryOut:
    def test_restores_a_board_through_its_board_class(self, monkeypatch):
        restored = []

        class FakeBoard:
            def __init__(self, target):
                self.target = target

            def restore(self):
                restored.append(self.target)

        monkeypatch.setattr(preview.preview_board, "Board", FakeBoard)

        preview.carry_out([("restore-device", "pi@netmon.local")], command_of=lambda pid: None)

        assert restored == ["pi@netmon.local"]
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `uv run --frozen pytest tests/test_preview_flavors.py tests/test_preview.py -m tier0 -q`
Expected: FAIL (`preview_flavors` has no `Device`; `preview` has no `preview_board`).

- [ ] **Step 3: Implement**

In `tests/preview_flavors.py` add `import preview_board` next to the other imports and, before `flavor_for`:

```python
class Device:
    def show(self, cleanup: ExitStack, settings: Settings, update: Callable[..., None]) -> Shown:
        board = preview_board.Board(settings.target)
        board.check_kiosk()
        update(device=settings.target)
        cleanup.callback(board.restore)
        tunnel = board.open_tunnel(settings.broker, DEV_PORT, preview_kiosk.INSPECTOR_PORT)
        cleanup.callback(board.close_tunnel, tunnel)
        update(tunnel=tunnel.pid)
        board.install(settings.broker)
        return Shown(f"http://localhost:{DEV_PORT}/", f"127.0.0.1:{preview_kiosk.INSPECTOR_PORT}")

    def stats_origin(self, settings: Settings) -> str | None:
        return preview_board.stats_origin(settings.target)
```
and replace the body of `flavor_for` after the `vm` case with `return Device()`:

```python
def flavor_for(settings: Settings, session_dir: Path) -> Flavor:
    if settings.flavor == "browser":
        return Browser()
    if settings.flavor == "vm":
        return Vm(session_dir)
    return Device()
```

In `tests/preview.py` add `import preview_board` (sorted before `preview_broker`), then replace `stale_actions` and `carry_out`:

```python
def stale_actions(record: dict, command_of: Callable[[int], str | None]) -> list[tuple[str, int | str | None]]:
    """Returns what a killed preview left behind that must be ended; raises AlreadyRunning if its owner is still alive."""
    owner = record.get("owner")
    if owner and "preview.py" in (command_of(owner) or ""):
        raise AlreadyRunning(f"a preview is already running (process {owner}); end it with Ctrl-C first")
    actions: list[tuple[str, int | str | None]] = []
    qemu, gradle, tunnel = record.get("qemu"), record.get("gradle"), record.get("tunnel")
    if qemu and "qemu-system" in (command_of(qemu) or ""):
        actions.append(("terminate", qemu))
    if gradle and "gradle" in (command_of(gradle) or "").lower():
        actions.append(("terminate-group", gradle))
    if tunnel and "ssh" in (command_of(tunnel) or ""):
        actions.append(("terminate", tunnel))
    if record.get("device"):
        actions.append(("restore-device", record["device"]))
    if record.get("broker"):
        actions.append(("stop-broker", None))
    return actions


def carry_out(actions: list[tuple[str, int | str | None]], command_of: Callable[[int], str | None] = preview_process.command_of) -> None:
    ended = []
    for action, subject in actions:
        try:
            if action == "terminate":
                os.kill(subject, signal.SIGTERM)
                ended.append(subject)
            elif action == "terminate-group":
                os.killpg(os.getpgid(subject), signal.SIGTERM)
                ended.append(subject)
            elif action == "restore-device":
                preview_board.Board(subject).restore()
            elif action == "stop-broker":
                preview_broker.stop()
        except ProcessLookupError:
            pass
    wait_until_gone(ended, command_of)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `uv run --frozen pytest -m tier0 -q`
Expected: PASS (all of tier 0, including the static checks).

- [ ] **Step 5: Try it on a board**

Needs a Pi with Pi Hero installed, reachable by ssh without a password prompt, and no other Gradle running. Replace `pi@netmon.local` with the board:

```bash
make preview-device TARGET=pi@netmon.local
```
Expected: `preview ready (device)` with `broker fixture on localhost:8080` and an inspector line; the panel shows the fixture's hosts from the dev server; Safari shows the Web Inspector with the live DOM; the status bar shows the board's CPU (not empty). Check on the board:

```bash
ssh pi@netmon.local 'ls /run/netmon-preview /run/systemd/system/pihero-kiosk.service.d; systemctl show pihero-kiosk -p DropInPaths'
```
Expected: the conf and the drop-in are listed. Edit a CSS file under `src/` and watch the panel update. Then:

- `BROKER=device`: `make preview-device TARGET=pi@netmon.local BROKER=device` shows the board's own scan data.
- `BROKER=localhost:8080` while a `make broker` runs in another terminal shows the same fixture without starting a second container.
- Ctrl-C: `ssh pi@netmon.local 'ls /run/netmon-preview 2>&1; systemctl show pihero-kiosk -p DropInPaths'` shows the directory gone and no drop-in; the panel is back on its own page after the restart.
- `kill -9` the `preview.py` process (find it with `pgrep -f 'preview.py --on device'`), then start `make preview-device TARGET=pi@netmon.local` again: it ends the old tunnel and Gradle, restores the board, and starts. If it fails with `port 8080 is taken`, a container of the killed run survived: that is the existing `stop-broker` action's job; read `dist/preview/session.json` and report what it held.

Record in the spec's Open section what the board showed for each of its open points: remote forwards, the inspector's target list, and whether the status bar's CPU appeared.

- [ ] **Step 6: Commit**

```bash
git add tests/preview.py tests/preview_flavors.py tests/test_preview.py tests/test_preview_flavors.py docs/superpowers/specs/2026-10-03-preview-flavors-design.md
git commit -m "feat(preview): add preview-device, the page in a real Pi's kiosk"
```

---

### Task 7: Run configurations and the README

**Files:**
- Delete: `.run/netmon-web-display [jsBrowserDevelopmentRun --continuous].run.xml`
- Create: `.run/preview-browser.run.xml`, `.run/preview-device.run.xml`
- Modify: `.run/preview.run.xml` stays; add `.run/preview-vm.run.xml`
- Modify: `README.md` (the section "Run the web display component locally")

- [ ] **Step 1: Replace the compound configuration**

```bash
git rm ".run/netmon-web-display [jsBrowserDevelopmentRun --continuous].run.xml"
for flavor in browser vm; do
  sed "s/name=\"preview\"/name=\"preview-$flavor\"/; s/make preview\"/make preview-$flavor\"/" .run/preview.run.xml > ".run/preview-$flavor.run.xml"
done
```

Write `.run/preview-device.run.xml` by hand (BSD `sed` has no `\n` in a replacement); it is `.run/preview.run.xml` with the name, the command and an environment:

```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="preview-device" type="ShConfigurationType">
    <option name="SCRIPT_TEXT" value="make preview-device" />
    <option name="INDEPENDENT_SCRIPT_PATH" value="true" />
    <option name="SCRIPT_PATH" value="" />
    <option name="SCRIPT_OPTIONS" value="" />
    <option name="INDEPENDENT_SCRIPT_WORKING_DIRECTORY" value="true" />
    <option name="SCRIPT_WORKING_DIRECTORY" value="$PROJECT_DIR$" />
    <option name="INDEPENDENT_INTERPRETER_PATH" value="true" />
    <option name="INTERPRETER_PATH" value="/bin/zsh" />
    <option name="INTERPRETER_OPTIONS" value="" />
    <option name="EXECUTE_IN_TERMINAL" value="true" />
    <option name="EXECUTE_SCRIPT_FILE" value="false" />
    <envs>
      <env name="TARGET" value="pi@netmon.local" />
    </envs>
    <method v="2" />
  </configuration>
</component>
```

Then `grep -c 'make preview-' .run/preview-*.run.xml`.

Expected: `.run/preview-device.run.xml` has `name="preview-device"`, `SCRIPT_TEXT" value="make preview-device"` and an `<envs>` block with `TARGET`. `.run/broker.run.xml`, `.run/preview.run.xml` and `.run/netmon-web-display dev server.run.xml` stay as they are (the last one is the IDE's way to debug the Kotlin/JS build).

- [ ] **Step 2: Rewrite the README section**

In `README.md` replace everything from `#### Run the web display component locally` up to (not including) `### Build and test the packages` with:

````markdown
#### Run the web display component locally

Three make targets show the page while you edit it. All read it from Gradle's dev server on port 8081, take the same
variables and have a Web Inspector on the page. They differ in where the page is shown:

```shell
make preview-browser                         # any browser: the fastest, with that browser's rendering and its own developer tools
make preview-vm                              # the kiosk's own WPE WebKit, 800 by 480, in a QEMU window: exact rendering, the Mac's speed (also: make preview)
make preview-device TARGET=pi@netmon.local   # the kiosk of a real Pi: the panel's own CPU use, the slowest
```

| Variable  | Default   | Meaning                                                                                                                         |
|-----------|-----------|---------------------------------------------------------------------------------------------------------------------------------|
| `BROKER`  | `fixture` | `fixture`: a Mosquitto container holding the `SCAN` hosts, started and stopped by the command. `device`: the Pi's own broker (`preview-device` only). `HOST:PORT`: that broker, nothing started; `localhost` is the Mac |
| `SCAN`    | `14+39`   | Recent and stable hosts of the fixture; `14+39x2` publishes two scans                                                           |
| `INSPECT` | `Safari`  | What opens once the session is up: the page (`preview-browser`) or the kiosk's Web Inspector; `INSPECT=0` opens nothing         |
| `TARGET`  |           | `preview-device` only: `user@host[:port]` of the Pi, which needs Pi Hero's `pihero-kiosk` and ssh access without a prompt       |

The broker is a container, so a scan can be replaced by hand while you watch (see
[Publish a scan to the preview's broker](#publish-a-scan-to-the-previews-broker)). Only one of the three runs at a time:
Gradle allows one build per project directory, so stop them before `make test-js`, `make test-layout` or any other
`./gradlew`. `make broker` runs only the fixture, for the IDE's dev server run configuration.

The first `make preview-vm` builds a base disk (about 2.5 minutes, cached under `~/.cache/pihero/preview`); later ones start
in about 10 seconds. It needs QEMU, Podman and Accessibility permission for your terminal (to size the window).

`make preview-device` changes nothing lasting on the Pi. One `ssh` connection carries the page, the fixture broker and the
inspector between the Mac and the Pi, and the kiosk reads its session settings from a drop-in under `/run`, which Ctrl-C
removes and a reboot wipes. The page is the development bundle, so its CPU and memory use is higher than what `make deploy`
installs; compare flavors and edits with each other, not with the production figures. The status bar's CPU figure is the
Pi's own, proxied from its `stats.json`.
````

Check that the old README text about `make broker` followed by `./gradlew jsBrowserDevelopmentRun --continuous` is gone: `grep -n 'jsBrowserDevelopmentRun' README.md` should print nothing. Check the `make test-preview` line in "Build and test the packages" still reads correctly.

- [ ] **Step 3: Check**

Run: `uv run --frozen pytest -m tier0 -q`
Expected: PASS.

Open the README in the IDE and run the inspections on the changed files (`README.md`, the three new `.run` files, every Python file this plan touched) with `mcp__idea__get_file_problems` and `errorsOnly: false`; fix what they report or name why it stays.

- [ ] **Step 4: Commit**

```bash
git add .run README.md
git commit -m "docs(readme): describe the three preview targets and their variables"
```

---

## Self-Review

- **Spec coverage:** Targets and the alias (Task 4, `test_makefile.py`); options and their defaults (Tasks 1 and 3); `BROKER` forms and the taken-port failure (Task 1); one at a time and the dev-server failure (Tasks 2 and 4); the four-step flavor table (Task 4's `Browser` and `Vm`, Task 6's `Device`); the reverse forwards, the volatile drop-in, the page URL (Task 5); the stats proxy (Task 2); recovery (Task 6); run configurations and the README (Task 7); tests as the spec lists them, with the device flavor checked by hand in Task 6 step 5. The spec's "browser flavor adds: the fixture holds the hosts and the dev server's page loads" is the by-hand check of Task 4 step 7, not an automated test, because it needs Gradle for minutes; this is the infeasible-in-CI case the testing rule asks to name.
- **Placeholders:** none; the one deliberately temporary line (`flavor_for` raising for `device`) is replaced in Task 6 and says so.
- **Type consistency:** `Broker(kind, host, port)` from Task 1 is what Tasks 3, 5 and 6 construct; `Settings.from_environ(flavor, environ)` from Task 3 is what Task 4's `main` calls; `Shown(page, inspector=None)`, `show(cleanup, settings, update)` and `stats_origin(settings)` have the same shape in `Browser`, `Vm` and `Device`; `Board.install(broker, timeout, sleep, clock)` is called as `install(settings.broker)` and, in tests, with `sleep=` and `clock=`; `stale_actions` and `carry_out` share the `("restore-device", target)` pair.
