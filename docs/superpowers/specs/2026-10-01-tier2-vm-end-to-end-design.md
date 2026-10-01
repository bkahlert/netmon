# Tier 2: the device file in a VM and the display end to end

Date: 2026-10-01. Status: approved design, ready for planning.

## Intent

Nothing in netmon's tests runs the whole thing. Tier 0 looks at the package files, tier 1 installs the packages into a
systemd container and checks that services come up and go away cleanly, and the ssh mode runs the same checks against the
board, where the scanner also proves that a scan completes and is retained. No test boots the device file, so a mistake in
[devices/sample/user-data](../../../devices/sample/user-data) shows up at the first flash. No test watches the display
render what the scanner published; the only such check was done by hand with a headless browser during the 2026 toolchain
upgrade.

Tier 2 closes both gaps on the Mac. The pihero testkit netmon already depends on boots a QEMU machine with the real
Raspberry Pi OS Lite root filesystem from a device directory and serves freshly built packages from a local apt server;
pihero uses it for its own packages, netmon has not wired it up. With it, the sample device file provisions a VM, the
scanner scans QEMU's small private network, and a WebKit browser on the Mac, the engine family the kiosk runs, loads the
packaged display through the VM's lighttpd, subscribes to the VM's Mosquitto, and shows the hosts the scanner found.

The display on the board also renders differently from the page in a desktop browser, accepted for the Pi Hero 2 move
but not understood. Such a difference has three possible sources: WebKit against Chrome, the 800×480 panel against a
desktop window, and the board's older WPE WebKit. Tier 2 takes on the first two: the test runs on WebKit and leaves a
screenshot at the panel's size after every run, and a make target opens the page in that WebKit at that size against the
VM or any broker, so a rendering question is looked at on the Mac first. The third source is for the follow-ups.

Success looks like this: `make test-tier2` boots the VM, and within about ten minutes reports that cloud-init finished,
no unit failed, both `bootconfig` lines survived the first-boot reboot, the scanner completed and retained a scan without
falling back to unprivileged mode, and the display page lists the gateway as up; `dist/tier2/display.png` shows the page
at 800×480. `make release` runs it before tagging. The same display test later runs against the board with
`--target=ssh`, which is the first piece of the board soak that follows this work.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Where the VM comes from | The testkit's tier 2 as pinned today (v2.2.0), unchanged | QEMU `virt`, the Raspberry Pi OS rootfs, cloud-init from a device directory, the local apt repo at `10.0.2.2:8000`, reboot handling and SSH access all exist; netmon adds a device file and tests, no pihero release is needed |
| The device file the VM boots | Generated at test time from `devices/sample/user-data` into `dist/vm-device/`, with three edits and nothing else | The test then proves the file users are told to copy; a committed second file would drift. The edits: the `users` entry becomes the testkit's user `pihero` with its public key, because that is who the harness logs in as; the netmon apt source becomes `http://10.0.2.2:8000/` with `Trusted: yes`, the server with this run's packages; `network-config` is left out, since the VM has no `wlan0` and without the file cloud-init gives the one Ethernet port DHCP, as pihero's own VM runs |
| Where the generation happens | A root [conftest.py](../../../conftest.py) sets `--device` to the generated directory when `--target=vm` is given without one; the generator is a small module under `tests/` with its own tier 0 test | `uv run pytest --target=vm` works from any directory and never boots the testkit's own device by accident; the three edits are plain logic and tested without a VM |
| The pihero source | Stays the public repository, as in the sample | `pihero`, `pihero-avahi`, `pihero-kiosk` and `pihero-usb-gadget` come from where the board gets them; the VM has internet through QEMU |
| Browser | Playwright's WebKit, installed with `playwright install webkit` into Playwright's own cache | The kiosk is cog, WPE WebKit: JavaScriptCore and WebCore, not V8 and Blink. Playwright's WebKit is a recent upstream build, on Linux the WPE flavour; it is newer than the board's Debian-packaged WPE, so a feature that arrived in between can still pass here and fail on the board, which the virtual-GPU follow-up closes. The download is a few hundred megabytes outside the repository and never involves the user's own browser |
| Driving the browser | The `playwright` Python package, synchronous API, in the dev group; no pytest plugin | One fixture launching headless WebKit is all the test needs |
| Reaching the VM's ports | An SSH tunnel built from the target's connection details, forwarding two free local ports to the VM's 80 and 8080 | The testkit forwards only port 22 and exposes the key, port and user; a tunnel needs no testkit change and the same fixture serves `--target=ssh`, where it tunnels through the user's SSH configuration |
| Test markers | `boot` for the boot tests and the display test | They need a booted system, so they run in the VM and on the board and are skipped in the container by the plugin |
| The scan tests | Skip only on podman, no longer everywhere but ssh | QEMU's user-mode network is `10.0.2.0/24`, inside the scanner's size filter; nmap's ARP scan gets answers from the gateway `10.0.2.2` and the DNS server `10.0.2.3`, so a scan completes in seconds |
| Memory | The scanner's `MemoryCurrent` and `MemoryPeak` after its first scan are reported in the run's output, not asserted | The footprint work needs a baseline from the packaged JVM with the unit's options before a threshold means anything |
| Seeing the page | The display test saves `dist/tier2/display.png` at 800×480 once the host is shown; `make display URL=…` opens Playwright's WebKit headed at the same size | A rendering difference is looked at, not inferred from assertions; the panel's size is the one that matters, and the engine is the kiosk's family. The target takes any page URL, so it serves the VM, the board and a local dev server alike |
| CI | Not run in CI for now | GitHub's Arm runners offer no hardware virtualization; a weekly job under software emulation, as pihero runs, is a follow-up |
| Scope | No testkit change, no new display feature, no kiosk in the VM | The kiosk is gated on `/dev/dri/card*` and stays skipped; running cog in the VM is the virtual-GPU follow-up |

## Commands

```shell
make browser                   # playwright install webkit, once; a no-op afterwards
make vm-prepare                # build and cache the tier 2 base image (~/.cache/pihero)
make test-tier2                # boot the VM from the sample device file and run 'installed or boot'
make vm                        # boot it and keep it running; prints the ssh command
make test-all                  # tiers 0 to 2
make release VERSION=X.Y.Z     # runs test-all, then tags
make display URL='http://netmon.local/?broker.host=netmon.local&broker.port=8080'   # the page in WebKit at 800×480
uv run pytest -m boot --target=ssh --target-uri=pi@netmon.local   # the boot and display tests against the board
```

`make display` is `playwright open` with the WebKit browser and an 800×480 viewport; it opens whatever URL it is given,
the running VM's forwarded ports, the board, or `jsBrowserDevelopmentRun` on the Mac.

`make test-tier2` expects the Gradle outputs like `make test-tier1` does: `make gradle` or `make build` first. QEMU comes from
Homebrew (`brew install qemu`), which the README states next to the other tools.

## Components

### The device directory

A module under `tests/` reads `devices/sample/user-data`, applies the three edits, and writes `user-data` into
`dist/vm-device/`. The testkit's public key is read from the installed package
(`pihero_testkit/keys/pihero-testkit.pub`), so a testkit upgrade that rotates the key needs no change here. The root
`conftest.py` calls it from `pytest_configure` when `--target=vm` is given and `--device` is not, and sets the option.
Everything else in the file, the time-sync wait, the packages, the kiosk configuration, the two `bootconfig`
lines and the `power_state` reboot, runs as written. The testkit's bootfs builder adds `meta-data` itself.

### Boot tests

[tests/test_boot.py](../../../tests/test_boot.py), marked `boot`:

- cloud-init reports done with no errors. Raspberry Pi OS's own warning about the missing `cc_netplan_nm_patch` module is
  the one recoverable error accepted, since it is not netmon's and the board shows it too.
- No unit failed.
- `/proc/cmdline` contains `video=HDMI-A-1:800x480M@60e` and `cgroup_enable=memory`. This proves the two `bootconfig`
  lines, the reboot request they raise, and the harness's handling of that reboot.
- `pihero-kiosk` is inactive with its condition unmet, not failed.
- The scanner's `MemoryCurrent` and `MemoryPeak` are read after the first scan and reported.

### Scanner tests

The two tests in [packages/netmon-scanner/tests/test_installed.py](../../../packages/netmon-scanner/tests/test_installed.py)
that wait for a completed scan and read the retained scan from the broker skip on podman only. Their assertion that nmap did
not switch to unprivileged mode now runs on a real kernel with a real systemd, which proves the unit's capabilities.

### The display test

[tests/test_display.py](../../../tests/test_display.py), marked `boot`. A session fixture opens the tunnel: for the VM
with the testkit's key, port and user, for `--target=ssh` with the target URI, in both cases `ssh -N -L` to two free local
ports. A second fixture launches Playwright's WebKit headless. The test waits for the scanner's "completed and published"
log line first, so the retained scan is younger than the display's five-minute freshness threshold, then loads
`http://127.0.0.1:<port>/?broker.host=127.0.0.1&broker.port=<port>` and waits for a host element with `data-status="up"`
whose IP line reads `10.0.2.2`. On the board the expected host is the gateway of the network the board is on, read from
the retained scan rather than hard-coded. The page is 800×480, the panel's size, and once the host is shown the test
writes `dist/tier2/display.png` (`dist/ssh/display.png` on the board), so a run leaves a picture of the page as WebKit
renders it at that size.

What this proves: the packaged bundle is served by lighttpd at the root, connects to Mosquitto over websockets on 8080,
decodes the retained scan event and renders its hosts, on a WebKit engine at the panel's size. What it does not prove:
that cog shows it on a panel, nor how the board's older WPE WebKit renders it.

### Make and documentation

`Makefile` gains `browser`, `vm-prepare`, `vm`, `test-tier2`, `test-all` and `display`; `release` runs `test-all` as
pihero's does. The README's build-and-test section gets the tier 2 lines, the QEMU and WebKit downloads, and `make display`
as the way to look at the page the way the kiosk's engine family renders it. `devices/README.md` notes that the sample is
the file tier 2 boots.

## Failure modes

- The VM does not boot or cloud-init fails: the testkit raises with the serial log path and cloud-init's log tail. Nothing
  to add.
- The time-sync wait in the sample's `bootcmd` blocks: the VM's clock is right from the start and NTP works through QEMU,
  so this should cost seconds. If it reaches the five-minute timeout, that is a finding about the sample, not something
  the test works around.
- The scan is older than five minutes when the page loads and the display drops it: the test waits for the log line before
  opening the page.
- A local port is taken: the tunnel picks free ports as the testkit picks its SSH port.
- Playwright's WebKit is not installed: the fixture fails with the `make browser` command in its message.
- The gateway does not answer ARP in some QEMU version: the scan test still passes with the VM's own address up; the
  display test fails with the page's host list in its message, which tells the difference at once.

## Follow-ups, in order

1. **The kiosk in the VM.** A spike in a pihero branch: give the testkit's VM a `virtio-gpu-pci` device and a QEMU monitor
   socket, boot netmon's device file, and see whether `pihero-kiosk` starts and cog paints anything. If it does, QEMU's
   `screendump` gives screenshots of the real kiosk and `systemctl show` gives cog's memory without touching the board; a
   testkit release then carries the change and netmon's tier 2 grows a screenshot comparison. If cog cannot render on the
   virtual GPU, the board stays the only place to see the kiosk.
2. **The board soak.** A few minutes against the running board over ssh: uptime continuity, `NRestarts` of kiosk and
   scanner, per-unit memory and swap under the caps, no OOM in the journal, and the display test above through the user's
   SSH configuration. How to see what the panel shows beyond that is a design question of its own; the display reporting
   its state to the broker is the leading option. For debugging the board's rendering by hand, WPE WebKit's remote
   inspector, switched on through the kiosk's environment in the device file, lets the Mac inspect the page cog shows;
   whether the board's memory allows it is part of that question.
3. **The footprint.** The scanner and the kiosk need about 600 MB on a 415 MB board, which only works because zram
   compresses swap and is why apt cannot run next to them; the board's device file stops both around every dpkg run
   with an apt hook. The sample deliberately has no such hook: the goal is a footprint small enough that apt runs next
   to the live stack and the hook becomes unnecessary. With tier 2 measuring the scanner's memory with the unit's
   `JAVA_TOOL_OPTIONS`, tier 1 proving those options reach the JVM whole, and the display test catching a scan cadence that
   breaks the freshness threshold, metaspace and code-cache limits, WPE memory settings and a slower cadence can be tried
   with a safety net.
4. **Weekly CI.** Tier 2 under software emulation on a schedule, as pihero's "what rotted" signal.
