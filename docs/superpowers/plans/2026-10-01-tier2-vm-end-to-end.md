# Tier 2: the device file in a VM and the display end to end — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `make test-tier2` boots the sample device file in the testkit's QEMU VM, proves the provisioning, lets the scanner scan QEMU's network, and shows the result in Playwright's WebKit at 800×480, leaving a screenshot.

**Architecture:** Nothing changes in the pihero testkit. Netmon adds a generator that renders `devices/sample/user-data` for the VM, a root `conftest.py` that hands the generated directory to the testkit's `--device` option, two test modules marked `boot` (provisioning checks and the display test), a helper module for them, and make targets. The display test reaches the VM through an SSH tunnel and drives Playwright's WebKit from the Mac.

**Tech Stack:** pytest 9 with pytest-testinfra, pihero-testkit v2.2.0 (`--target=vm`, QEMU `virt`, Raspberry Pi OS Lite arm64 rootfs), Playwright 1.63 for Python with its WebKit build, OpenSSH `-L` tunnels, GNU make, uv.

**Spec:** [docs/superpowers/specs/2026-10-01-tier2-vm-end-to-end-design.md](../specs/2026-10-01-tier2-vm-end-to-end-design.md)

## Global Constraints

- The pihero testkit stays at the pinned `v2.2.0`; no change in the pihero repository.
- Python ≥ 3.13; everything runs through `uv run --frozen`, so `uv.lock` is updated and committed with every dependency change.
- The VM's device file is generated from `devices/sample/user-data` with exactly three edits: the user entry's name and key, the netmon apt source, and no `network-config`.
- The browser is Playwright's WebKit, installed with `playwright install webkit`; nothing launches or touches the user's own browser.
- The display test renders at 800×480 and writes `dist/tier2/display.png` (VM) or `dist/<target>/display.png`.
- Tests needing a booted system carry `pytestmark = pytest.mark.boot`; tier 0 tests carry `pytest.mark.tier0`.
- Commits: Conventional Commits, lowercase imperative description, header ≤ 72 characters, no AI-attribution trailers. Types used here: `build`, `test`, `docs`.
- Test files: test cases first, helpers and fixtures last; no comments or docstrings in tests. Regular expressions use named or non-capturing groups only.
- With the JetBrains MCP connected, run `mcp__idea__get_file_problems` with `errorsOnly: false` on every file a task changed and fix or explain each finding.
- One Gradle or VM run at a time, in the foreground.

## Review Focus

- A sample `user-data` whose `users:` block gains a second user or loses its key line must not produce a half-edited VM file: the generator raises. Pinned in Task 2 (`test_on_two_users_raises`, `test_on_a_file_without_a_users_block_raises`).
- An explicit `--device=…` on the command line must win over the generated directory. Pinned in Task 3 (`test_on_an_explicit_device_leaves_it_alone`).
- A board with two default routes (cable and Wi-Fi in one LAN) has one gateway; the first route's gateway is used. Pinned in Task 5 (`test_on_two_default_routes_returns_the_first`).
- The gateway `192.168.16.1` must not be satisfied by a host `192.168.16.10` on the page: the IP match is anchored. Pinned in Task 5 (`test_matches_the_ip_and_not_a_longer_one`).
- cloud-init "degraded" with any warning other than Raspberry Pi OS's `cc_netplan_nm_patch` must fail the boot test. Pinned in Task 5 (`test_reports_any_other_warning`).

---

### Task 1: Playwright's WebKit as a dev dependency

**Files:**
- Modify: `pyproject.toml` (dev group)
- Modify: `uv.lock` (by `uv add`)
- Modify: `Makefile` (`browser` target, `.PHONY`)

**Interfaces:**
- Produces: `uv run --frozen playwright …` works; `make browser` installs WebKit into Playwright's cache (`~/Library/Caches/ms-playwright` on macOS).

- [ ] **Step 1: Add the dependency**

Run: `uv add --group dev playwright`
Expected: `pyproject.toml` has `dev = ["pihero-testkit", "playwright>=1.63.0"]` (or the current release) and `uv.lock` gained `playwright`, `greenlet` and `pyee`.

- [ ] **Step 2: Verify the Arm wheels the CI runners need are in the lock**

Run: `grep -c 'playwright-.*manylinux_2_17_aarch64' uv.lock && grep -c 'playwright-.*macosx_11_0_arm64' uv.lock`
Expected: both counts are `1` or more. If either is `0`, stop: `uv sync --frozen` would fail on `ubuntu-24.04-arm`.

- [ ] **Step 3: Add the make target**

In `Makefile`, extend `.PHONY` and add after `build`:

```make
browser: ## download Playwright's WebKit, the kiosk's engine family, for the display tests
	@$(UV) playwright install webkit
```

The `.PHONY` line becomes:

```make
.PHONY: help gradle build browser test-jvm test-js test-tier0 test-tier1 test-tier2 test test-all vm-device vm-prepare vm display deploy clean release
```

- [ ] **Step 4: Install the browser and prove it launches headless**

Run: `make browser`
Expected: Playwright downloads WebKit (a few hundred MB, once); a second `make browser` prints nothing new.

Run:
```shell
uv run --frozen python -c "
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b = p.webkit.launch(); page = b.new_page(viewport={'width': 800, 'height': 480})
    page.set_content('<title>webkit ok</title>'); print(page.title(), page.viewport_size); b.close()
"
```
Expected: `webkit ok {'width': 800, 'height': 480}`

- [ ] **Step 5: Inspections and commit**

Run `mcp__idea__get_file_problems` on `pyproject.toml` and `Makefile`.

```bash
git add pyproject.toml uv.lock Makefile
git commit -m "build: playwright's webkit for the display tests"
```

---

### Task 2: The VM's device file, generated from the sample

**Files:**
- Create: `tests/vm_device.py`
- Create: `tests/test_vm_device.py`
- Modify: `tests/test_static.py` (`device_files()` also yields the generated file)
- Modify: `pyproject.toml` (`pythonpath = ["tests"]` under `[tool.pytest.ini_options]`)

**Interfaces:**
- Produces: module `vm_device` with `render(sample: str, key: str, user: str = "pihero", url: str = "http://10.0.2.2:8000/") -> str`, `block(text: str, start: str) -> str`, `write(out: Path = OUT, sample: Path = SAMPLE) -> Path`, constants `OUT` (`dist/vm-device`), `SAMPLE`, `PUBLIC_KEY` (Path of the testkit's `.pub`). Running `python tests/vm_device.py` writes `dist/vm-device/user-data` and prints the directory.

- [ ] **Step 1: Make `tests/` importable**

In `pyproject.toml`, under `[tool.pytest.ini_options]`, add:

```toml
pythonpath = ["tests"]
```

- [ ] **Step 2: Write the failing tests**

`tests/test_vm_device.py`:

```python
from pathlib import Path

import pytest

import vm_device

pytestmark = pytest.mark.tier0
ROOT = Path(__file__).resolve().parents[1]
SAMPLE = (ROOT / "devices" / "sample" / "user-data").read_text()
KEY = "ssh-ed25519 AAAATEST pihero-testkit"


class TestRender:
    def test_renames_the_user_and_sets_the_testkit_key(self):
        result = vm_device.render(SAMPLE, key=KEY)

        assert "  - name: pihero\n" in result
        assert "  - name: pi\n" not in result
        assert f"    ssh_authorized_keys:\n      - {KEY}\n" in result

    def test_points_the_netmon_source_at_the_local_repository(self):
        result = vm_device.render(SAMPLE, key=KEY)

        assert "      URIs: http://10.0.2.2:8000/\n      Suites: ./\n      Trusted: yes\n" in result
        assert "bkahlert.github.io/netmon" not in result
        assert "bkahlert.github.io/pihero" in result

    def test_changes_nothing_else(self):
        result = vm_device.render(SAMPLE, key=KEY)

        assert without_edited_blocks(result) == without_edited_blocks(SAMPLE)

    def test_on_a_file_without_a_users_block_raises(self):
        with pytest.raises(ValueError, match="users:"):
            vm_device.render("#cloud-config\nhostname: x\n", key=KEY)

    def test_on_two_users_raises(self):
        two = SAMPLE.replace("rpi:\n", "  - name: second\n    ssh_authorized_keys:\n      - ssh-ed25519 BBBB second\nrpi:\n")

        with pytest.raises(ValueError, match="one user"):
            vm_device.render(two, key=KEY)


class TestWrite:
    def test_writes_only_user_data_with_the_testkit_key(self, tmp_path):
        out = vm_device.write(tmp_path / "vm-device")

        assert [p.name for p in out.iterdir()] == ["user-data"]
        text = (out / "user-data").read_text()
        assert text.startswith("#cloud-config\n")
        assert vm_device.PUBLIC_KEY.read_text().strip() in text


def without_edited_blocks(text: str) -> str:
    for start in ("users:", "  - path: /etc/apt/sources.list.d/netmon.sources"):
        text = text.replace(vm_device.block(text, start), "")
    return text
```

- [ ] **Step 3: Run them to see them fail**

Run: `uv run --frozen pytest tests/test_vm_device.py -q`
Expected: collection error `ModuleNotFoundError: No module named 'vm_device'`.

- [ ] **Step 4: Write the generator**

`tests/vm_device.py`:

```python
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
    rekeyed = re.sub(r"^(?P<prefix>      - ).*$", lambda m: m["prefix"] + key, renamed, count=1, flags=re.M)
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
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `uv run --frozen pytest tests/test_vm_device.py -q`
Expected: `6 passed`.

- [ ] **Step 6: Validate the generated file against cloud-init's schema in tier 0**

In `tests/test_static.py`, add `import vm_device` after `from pihero_testkit import tools`, and change `device_files()` to:

```python
def device_files():
    yield from (ROOT / "devices").glob("*/user-data")
    yield vm_device.write() / "user-data"
```

Run: `uv run --frozen pytest tests/test_static.py -q -k schema`
Expected: `2 passed`, ids `sample` and `vm-device`, and `dist/vm-device/user-data` exists.

- [ ] **Step 7: Inspections and commit**

Run `mcp__idea__get_file_problems` on `tests/vm_device.py`, `tests/test_vm_device.py`, `tests/test_static.py`, `pyproject.toml`.

```bash
git add pyproject.toml tests/vm_device.py tests/test_vm_device.py tests/test_static.py
git commit -m "test: generate the VM's device file from the sample"
```

---

### Task 3: The VM boots the generated file by default, and the make targets

**Files:**
- Create: `conftest.py` (repository root)
- Modify: `tests/test_vm_device.py` (two tests for the hook)
- Modify: `Makefile` (`QEMU_ACCEL`, `URL`, `vm-device`, `vm-prepare`, `vm`, `test-tier2`, `test-all`, `display`; `release` runs `test-all`)

**Interfaces:**
- Consumes: `vm_device.write()` and `vm_device.OUT` from Task 2.
- Produces: `uv run pytest --target=vm …` without `--device` boots `dist/vm-device`; `make test-tier2`, `make vm`, `make display URL=…`.

- [ ] **Step 1: Write the failing tests**

Append to `tests/test_vm_device.py`, before `without_edited_blocks`:

```python
class TestPytestConfigure:
    def test_on_the_vm_target_without_a_device_generates_it(self):
        (vm_device.OUT / "user-data").unlink(missing_ok=True)

        collect_only("--target=vm")

        assert (vm_device.OUT / "user-data").exists()

    def test_on_an_explicit_device_leaves_it_alone(self):
        (vm_device.OUT / "user-data").unlink(missing_ok=True)

        collect_only("--target=vm", "--device=devices/sample")

        assert not (vm_device.OUT / "user-data").exists()
```

and add to the helpers at the bottom:

```python
def collect_only(*options: str) -> None:
    subprocess.run(
        [sys.executable, "-m", "pytest", "--collect-only", "-q", "-p", "no:cacheprovider", *options, "tests/test_vm_device.py"],
        cwd=ROOT, check=True, capture_output=True, text=True,
    )
```

with `import subprocess` and `import sys` added to the imports.

- [ ] **Step 2: Run them to see them fail**

Run: `uv run --frozen pytest tests/test_vm_device.py -q -k PytestConfigure`
Expected: `test_on_the_vm_target_without_a_device_generates_it` fails (`assert False`); the other passes already.

- [ ] **Step 3: Write the root conftest**

`conftest.py` in the repository root:

```python
"""Tier 2 boots netmon's own device file: the sample rendered for the VM, unless a device directory is given."""
import vm_device


def pytest_configure(config):
    if config.getoption("--target") == "vm" and not config.getoption("--device"):
        config.option.device = str(vm_device.write())
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `uv run --frozen pytest tests/test_vm_device.py -q`
Expected: `8 passed`.

Run: `uv run --frozen pytest -q -m tier0`
Expected: all tier 0 tests pass; `dist/vm-device/user-data` is not touched by a podman-target run (the default).

- [ ] **Step 5: Add the make targets**

In `Makefile`, after `TARGET ?=` add:

```make
QEMU_ACCEL ?= hvf
URL ?=
```

After `test-tier1` add:

```make
test-tier2: ## boot a VM from the sample device file and run the installed and boot tests
	@$(UV) pytest -m 'installed or boot' --target=vm --qemu-accel=$(QEMU_ACCEL)
```

Change `test` and add `test-all`:

```make
test: test-jvm test-js test-tier0 test-tier1 ## JVM and JS unit tests, tiers 0 and 1, what CI runs

test-all: test test-tier2 ## everything, what make release runs

vm-device: ## render the sample device file for the VM into dist/vm-device
	@$(UV) python tests/vm_device.py

vm-prepare: ## build and cache the tier-2 base image under ~/.cache/pihero
	@$(UV) python -m pihero_testkit.prepare

vm: vm-device ## boot the tier-2 VM from the sample device file and keep it running
	@$(UV) python -m pihero_testkit.vm --keep --qemu-accel=$(QEMU_ACCEL) --device=dist/vm-device

display: ## open URL in Playwright's WebKit at the panel's 800x480 (make display URL='http://netmon.local/?broker.host=netmon.local&broker.port=8080')
	@test -n "$(URL)" || { echo "usage: make display URL='http://host/?broker.host=host&broker.port=8080'"; exit 2; }
	@$(UV) playwright open -b webkit --viewport-size=800,480 "$(URL)"
```

In `release`, replace `@$(MAKE) test` with `@$(MAKE) test-all`.

- [ ] **Step 6: Verify the targets**

Run: `make -n test-tier2 vm display`
Expected: the three commands printed as written above; `display` prints the usage line because `URL` is empty.

Run: `make vm-device && head -20 dist/vm-device/user-data`
Expected: the path `…/dist/vm-device` and a file whose user is `pihero` with the testkit's key.

Run: `make display URL='about:blank'`
Expected: a WebKit window of 800×480 opens with a blank page. Close it.

- [ ] **Step 7: Inspections and commit**

Run `mcp__idea__get_file_problems` on `conftest.py`, `tests/test_vm_device.py`, `Makefile`.

```bash
git add conftest.py tests/test_vm_device.py Makefile
git commit -m "build: tier 2 make targets, the VM boots the sample device file"
```

---

### Task 4: The scan tests run in the VM

**Files:**
- Modify: `packages/netmon-scanner/tests/test_installed.py:57-71`

**Interfaces:**
- Produces: `test_completes_and_publishes_a_scan` and `test_the_scan_is_retained_at_the_broker` skip on podman only.

- [ ] **Step 1: Change the two skip conditions**

In both tests replace, keeping the method body's indentation,

```python
if request.config.getoption("--target") != "ssh":
    pytest.skip("a scan of the container's /16 takes minutes; proven on a device")
```

with

```python
if request.config.getoption("--target") == "podman":
    pytest.skip("a scan of the container's /16 takes minutes; proven in the VM and on a device")
```

- [ ] **Step 2: Verify collection and the podman skip are unchanged**

Run: `uv run --frozen pytest --collect-only -q -m installed packages/netmon-scanner/tests/test_installed.py | tail -3`
Expected: the same number of tests as before (13 selected).

The behaviour in the VM is proven in Task 8.

- [ ] **Step 3: Inspections and commit**

Run `mcp__idea__get_file_problems` on `packages/netmon-scanner/tests/test_installed.py`.

```bash
git add packages/netmon-scanner/tests/test_installed.py
git commit -m "test(scanner): the scan tests run in the VM"
```

---

### Task 5: Helpers for tests against a booted target

**Files:**
- Create: `tests/booted.py`
- Create: `tests/test_booted.py`

**Interfaces:**
- Consumes: `pihero_testkit.vm.Vm` (attributes `key`, `port`, `user`) and `pihero_testkit.vm.SSH_OPTS`; `pihero_testkit.ssh.SshTarget` (attribute `uri`).
- Produces: module `booted` with `journal_until(host, needle: str, attempts: int = 45) -> str`, `unexpected_recoverable_errors(status: dict) -> list[str]`, `gateway_of(routes: str) -> str`, `exactly(ip: str) -> re.Pattern`, `class Tunnel(target)` with attributes `http`, `ws` (local ports) and `close()`, `free_port() -> int`, constant `KNOWN_CLOUD_INIT_WARNING = "cc_netplan_nm_patch"`.

- [ ] **Step 1: Write the failing tests**

`tests/test_booted.py`:

```python
import pytest

from booted import exactly, gateway_of, unexpected_recoverable_errors

pytestmark = pytest.mark.tier0


class TestGatewayOf:
    def test_returns_the_gateway_of_the_default_route(self):
        result = gateway_of("default via 10.0.2.2 dev eth0 proto dhcp src 10.0.2.15 metric 100\n")

        assert result == "10.0.2.2"

    def test_on_two_default_routes_returns_the_first(self):
        routes = "default via 192.168.16.1 dev eth0 proto dhcp metric 100\ndefault via 192.168.16.1 dev wlan0 proto dhcp metric 600\n"

        result = gateway_of(routes)

        assert result == "192.168.16.1"

    def test_on_no_default_route_raises(self):
        with pytest.raises(ValueError, match="default route"):
            gateway_of("")


class TestExactly:
    def test_matches_the_ip_and_not_a_longer_one(self):
        pattern = exactly("192.168.16.1")

        assert pattern.match("192.168.16.1")
        assert not pattern.match("192.168.16.10")


class TestUnexpectedRecoverableErrors:
    def test_accepts_the_netplan_warning_of_raspberry_pi_os(self):
        status = {"recoverable_errors": {"WARNING": ["Could not find module named cc_netplan_nm_patch"]}}

        result = unexpected_recoverable_errors(status)

        assert result == []

    def test_reports_any_other_warning(self):
        status = {"recoverable_errors": {"WARNING": ["Could not find module named cc_netplan_nm_patch", "Failed to install packages"]}}

        result = unexpected_recoverable_errors(status)

        assert result == ["Failed to install packages"]

    def test_on_no_recoverable_errors_is_empty(self):
        result = unexpected_recoverable_errors({"status": "done"})

        assert result == []
```

- [ ] **Step 2: Run them to see them fail**

Run: `uv run --frozen pytest tests/test_booted.py -q`
Expected: `ModuleNotFoundError: No module named 'booted'`.

- [ ] **Step 3: Write the helpers**

`tests/booted.py`:

```python
"""Helpers for tests against a booted target: the scanner's journal, cloud-init's status, the default gateway, and an SSH tunnel."""
import re
import socket
import subprocess
import time

from pihero_testkit.vm import SSH_OPTS, Vm

KNOWN_CLOUD_INIT_WARNING = "cc_netplan_nm_patch"


def journal_until(host, needle: str, attempts: int = 45) -> str:
    log = ""
    for _ in range(attempts):
        log = host.run("journalctl -u netmon-scanner -b --no-pager -o cat").stdout
        if needle in log:
            return log
        time.sleep(2)
    return log


def unexpected_recoverable_errors(status: dict) -> list[str]:
    """Return cloud-init's recoverable errors except Raspberry Pi OS's own warning about a module it names but does not ship."""
    return [message for messages in status.get("recoverable_errors", {}).values() for message in messages if KNOWN_CLOUD_INIT_WARNING not in message]


def gateway_of(routes: str) -> str:
    """Return the gateway of the first default route in the output of `ip -4 route show default`."""
    for line in routes.splitlines():
        words = line.split()
        if len(words) >= 3 and words[0] == "default" and words[1] == "via":
            return words[2]
    raise ValueError(f"no default route in {routes!r}")


def exactly(ip: str) -> re.Pattern:
    return re.compile(rf"^{re.escape(ip)}$")


class Tunnel:
    """Forwards two free local ports to the target's 80 and 8080 with `ssh -N -L` until closed."""

    def __init__(self, target):
        self.http = free_port()
        self.ws = free_port()
        forwards = ["-L", f"127.0.0.1:{self.http}:127.0.0.1:80", "-L", f"127.0.0.1:{self.ws}:127.0.0.1:8080"]
        if isinstance(target, Vm):
            command = ["ssh", "-N", *forwards, "-i", str(target.key), "-p", str(target.port), *SSH_OPTS, f"{target.user}@127.0.0.1"]
        else:
            user_host, _, port = target.uri.partition(":")
            command = ["ssh", "-N", *forwards, *(["-p", port] if port else []), "-o", "BatchMode=yes", user_host]
        self.process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        self._wait_listening()

    def _wait_listening(self, timeout: float = 30) -> None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError(f"the ssh tunnel exited: {self.process.stderr.read()}")
            try:
                with socket.create_connection(("127.0.0.1", self.http), timeout=1):
                    return
            except OSError:
                time.sleep(0.5)
        raise TimeoutError("the ssh tunnel did not come up")

    def close(self) -> None:
        self.process.terminate()
        self.process.wait(10)


def free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `uv run --frozen pytest tests/test_booted.py -q`
Expected: `7 passed`.

- [ ] **Step 5: Inspections and commit**

Run `mcp__idea__get_file_problems` on `tests/booted.py` and `tests/test_booted.py`.

```bash
git add tests/booted.py tests/test_booted.py
git commit -m "test: helpers for the tests against a booted target"
```

---

### Task 6: The boot tests

**Files:**
- Create: `tests/test_boot.py`

**Interfaces:**
- Consumes: `booted.journal_until`, `booted.unexpected_recoverable_errors`; the plugin's `host` fixture.
- Produces: the provisioning checks of the spec, run by `-m boot` in the VM and on the board.

- [ ] **Step 1: Write the tests**

`tests/test_boot.py`:

```python
import json

import pytest

from booted import journal_until, unexpected_recoverable_errors

pytestmark = pytest.mark.boot


class TestProvisioning:
    def test_cloud_init_finished_without_errors(self, host):
        status = json.loads(host.check_output("cloud-init status --long --format json"))

        assert status["errors"] == []
        assert unexpected_recoverable_errors(status) == []
        assert status["status"] == "done", status

    def test_no_unit_failed(self, host):
        failed = host.check_output("systemctl --failed --no-legend --plain").strip()

        assert failed == ""

    def test_the_bootconfig_lines_reached_the_kernel(self, host):
        cmdline = host.file("/proc/cmdline").content_string.split()

        assert "video=HDMI-A-1:800x480M@60e" in cmdline
        assert "cgroup_enable=memory" in cmdline

    def test_the_apt_hook_stops_and_starts_the_stack_around_dpkg(self, host):
        hook = host.file("/etc/apt/apt.conf.d/52netmon-dpkg").content_string

        assert 'DPkg::Pre-Invoke { "systemctl stop pihero-kiosk netmon-scanner || true"; };' in hook
        assert 'DPkg::Post-Invoke { "systemctl start netmon-scanner pihero-kiosk || true"; };' in hook


class TestKiosk:
    def test_is_skipped_by_its_condition_without_a_display_adapter(self, host):
        if host.file("/dev/dri").exists:
            pytest.skip("has a display adapter")

        states = host.check_output("systemctl show --property=ActiveState --property=ConditionResult --value pihero-kiosk.service").split()

        assert states == ["inactive", "no"]


class TestScanner:
    def test_reports_its_memory_after_the_first_scan(self, host, request):
        log = journal_until(host, "completed and published to", attempts=90)
        assert "completed and published to" in log

        show = host.check_output("systemctl show -p MemoryCurrent -p MemoryPeak netmon-scanner.service").splitlines()

        reporter = request.config.pluginmanager.get_plugin("terminalreporter")
        reporter.ensure_newline()
        reporter.write_line(f"netmon-scanner after the first scan: {' '.join(show)}")
        assert "MemoryCurrent=[not set]" not in show
        assert any(line.startswith("MemoryCurrent=") for line in show), show
```

- [ ] **Step 2: Verify collection and the podman skip**

Run: `uv run --frozen pytest --collect-only -q tests/test_boot.py`
Expected: 6 tests collected.

Run: `uv run --frozen pytest --collect-only -q -m boot --target=podman tests/test_boot.py -rs 2>&1 | tail -3`
Expected: the plugin marks them `skip` with "needs a booted system" (visible in `-rs` once run; `--collect-only` shows them collected). The VM run in Task 8 proves the assertions; if cloud-init's JSON on the image reports `"status": "degraded done"` instead of `"done"` with the warning in `recoverable_errors`, accept both values in the first test and note it in the commit body.

- [ ] **Step 3: Inspections and commit**

Run `mcp__idea__get_file_problems` on `tests/test_boot.py`.

```bash
git add tests/test_boot.py
git commit -m "test: the sample device file provisions a booted system"
```

---

### Task 7: The display test

**Files:**
- Create: `tests/test_display.py`

**Interfaces:**
- Consumes: `booted.Tunnel`, `booted.exactly`, `booted.gateway_of`, `booted.journal_until`; the plugin's `host` and `target` fixtures; Playwright's sync API.
- Produces: `tests/test_display.py::TestDisplay::test_shows_the_gateway_the_scanner_found`, writing `dist/tier2/display.png` in the VM and `dist/ssh/display.png` on the board.

- [ ] **Step 1: Write the test**

`tests/test_display.py`:

```python
from pathlib import Path

import pytest
from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

from booted import Tunnel, exactly, gateway_of, journal_until

pytestmark = pytest.mark.boot
PANEL = {"width": 800, "height": 480}


class TestDisplay:
    def test_shows_the_gateway_the_scanner_found(self, host, page, tunnel, screenshot):
        log = journal_until(host, "completed and published to", attempts=90)
        assert "completed and published to" in log
        gateway = gateway_of(host.check_output("ip -4 route show default"))

        page.goto(f"http://127.0.0.1:{tunnel.http}/?broker.host=127.0.0.1&broker.port={tunnel.ws}")
        shown = page.locator('.host[data-status="up"]').filter(has=page.locator(".font-mono", has_text=exactly(gateway)))
        try:
            shown.first.wait_for(timeout=120_000)
        except PlaywrightTimeoutError:
            pytest.fail(f"{gateway} is not shown as up; hosts on the page: {page.locator('.host .font-mono').all_inner_texts()}")
        page.screenshot(path=str(screenshot))

        assert shown.count() == 1
        assert screenshot.stat().st_size > 0


@pytest.fixture(scope="module")
def tunnel(target):
    tunnel = Tunnel(target)
    yield tunnel
    tunnel.close()


@pytest.fixture(scope="module")
def page():
    with sync_playwright() as playwright:
        try:
            browser = playwright.webkit.launch()
        except PlaywrightError as e:
            pytest.fail(f"Playwright's WebKit is not installed; run `make browser`\n{e}")
        page = browser.new_page(viewport=PANEL)
        yield page
        browser.close()


@pytest.fixture(scope="module")
def screenshot(request):
    target = request.config.getoption("--target")
    path = Path.cwd() / "dist" / ("tier2" if target == "vm" else target) / "display.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    return path
```

- [ ] **Step 2: Verify collection**

Run: `uv run --frozen pytest --collect-only -q tests/test_display.py`
Expected: 1 test collected, no import error (Playwright's import proves Task 1).

- [ ] **Step 3: Inspections and commit**

Run `mcp__idea__get_file_problems` on `tests/test_display.py`.

```bash
git add tests/test_display.py
git commit -m "test(display): the page shows the scan on webkit at the panel's size"
```

---

### Task 8: Run tier 2, fix what it finds, document

**Files:**
- Modify: `README.md` (Build and test the packages)
- Modify: `devices/README.md` (the sample is what tier 2 boots)
- Possibly modify: any file a finding points at

**Interfaces:**
- Consumes: everything above.
- Produces: a green `make test-tier2`, `dist/tier2/display.png`, the memory line in the output, and the documentation.

- [ ] **Step 1: Build the inputs**

Run: `make gradle` (if `build/libs/netmon-all.jar` or `build/dist/js/productionExecutable` is older than the sources), then `make vm-prepare`.
Expected: `prepare` downloads the pinned Raspberry Pi OS image once (~500 MB) and builds the base image under `~/.cache/pihero/base/<key>/`; a second run returns at once.

- [ ] **Step 2: Run tier 2**

Run: `make test-tier2 2>&1 | tee dist/tier2.log`
Expected within about ten minutes: the `installed` tests of both packages pass (the two scan tests now run), `tests/test_boot.py` passes with a line `netmon-scanner after the first scan: MemoryCurrent=… MemoryPeak=…`, `tests/test_display.py` passes, `dist/tier2/display.png` exists. Open the PNG and confirm it shows the Netmon page with the gateway's card.

- [ ] **Step 3: Iterate against a kept VM when something fails**

A full boot per attempt is slow. Keep one VM and point the `ssh` target at it:

```shell
make vm                                   # prints the ssh command, with the port and the key path
cat >> ~/.ssh/config <<'EOF'
Host netmon-vm
  HostName 127.0.0.1
  Port <port from make vm>
  User pihero
  IdentityFile <key path from make vm>
  StrictHostKeyChecking no
  UserKnownHostsFile /dev/null
EOF
uv run --frozen pytest -m boot --target=ssh --target-uri=netmon-vm
```

Remove the `Host netmon-vm` block afterwards. Mutating tests are skipped over ssh, so this covers the boot and display tests and the read-only installed ones.

Findings to expect and how to treat them: a cloud-init status field other than `done` (see Task 6 step 2); the `rpi:` key's interface settings failing in the VM, which would be a fourth edit to discuss, not to make silently; the time-sync wait reaching its five-minute timeout, which is a finding about the sample. Fix test code freely; anything that would change the sample device file or the packages is raised to the user first.

- [ ] **Step 4: Document**

In `README.md`, replace the shell block under "Build and test the packages" with:

```shell
# Gradle runs on JDK 17 (gradle/gradle-daemon-jvm.properties) and finds or downloads one itself
make build                                          # Gradle, then nfpm: dist/*.deb
make test                                           # tier 0 (static checks, unit tests) and tier 1 (install into a systemd container)
make test-tier2                                     # tier 2: boot a QEMU VM from devices/sample, scan, and show the page in WebKit
make deploy TARGET=pi@netmon.local                  # the built packages onto a device, no repository involved
```

and after the paragraph that names pihero-testkit add:

> Tier 2 needs QEMU (`brew install qemu`) and Playwright's WebKit (`make browser`, downloaded once into Playwright's cache). It
> boots the real Raspberry Pi OS root filesystem with [devices/sample/user-data](devices/sample/user-data), rendered for the VM
> by `tests/vm_device.py`, lets the scanner scan QEMU's network, and loads the page in WebKit at the panel's 800×480; the run
> leaves `dist/tier2/display.png`. `make vm` keeps the VM running for a look around, and `make display URL=…` opens any page,
> the VM's, the board's or a dev server's, in that WebKit at that size. `make release` runs tiers 0 to 2.

In `devices/README.md`, append to the first paragraph:

> `sample/` is also what tier 2 boots: `make test-tier2` renders it for the VM with the testkit's user and the local
> package repository and nothing else, so the file users copy is the file that is tested.

- [ ] **Step 5: Inspections and commit**

Run `mcp__idea__get_file_problems` on `README.md` and `devices/README.md`.

```bash
git add README.md devices/README.md
git commit -m "docs: tier 2 in the build and test instructions"
```

If Step 3 changed test code, commit those changes separately before this one with a `test:` header describing the finding.

- [ ] **Step 6: Final check**

Run: `uv run --frozen pytest -q -m tier0 && git status --short`
Expected: all tier 0 tests pass; the working tree is clean. Report the tier 2 duration, the memory line, and the screenshot path to the user.
