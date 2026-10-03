# The display's live preview in the kiosk's engine

## Intent

Editing the display should show the result on a screen that renders like the panel, within seconds, from one command.
"Like the panel" means the kiosk's own engine: WPE WebKit 2.48.3 under cog, the Pi OS fonts, no JIT, 800 by 480 pixels.
The command must leave nothing running when it ends.

There are two ways to look, and both stay. The *browser* way shows the page in any browser on the Mac: the fastest, with
that browser's rendering. The *kiosk* way shows it in the VM's WPE: slower to start and exact. Both look at the same dev
server and the same broker, both work without configuration, and the broker can be replaced.

Today the fast loop and the faithful loop are different tools, and neither brings a broker. This change joins them: the
Mac's dev server feeds the page to the kiosk of a short-lived VM, the VM's display is a window on the Mac, and one broker
with a fixture serves both ways.

## Today

| Step                                   | Engine                            | Cost                                            |
|----------------------------------------|-----------------------------------|-------------------------------------------------|
| `jsBrowserDevelopmentRun --continuous` | Chrome on the Mac                 | Seconds, but it needs a broker with data        |
| `make display URL=…`                   | Playwright's WebKit at 800 by 480 | The Mac's WebKit and fonts                      |
| `make vm`, `make test-tier2`           | The board's WPE in a QEMU VM      | Packages are built and installed first; minutes |
| `make deploy`                          | The board                         | Slowest                                         |

The engines differ in ways that matter. The [css layout spec](2026-10-03-css-layout-design.md) found that
`tan(atan2(…))` is right in Playwright's WebKit and wrong in WPE 2.48.3.

The ways to start the display are scattered: seven IDE run configurations in `.run/`, 19 Make targets, raw Gradle tasks and
the README. The scans for a fixture are defined in `scans()` of [layout.py](../../../tests/layout.py), in a commented-out
block of [main.kt](../../../src/jsMain/kotlin/com/bkahlert/netmon/main.kt), and in the README's `mqtt pub` examples.

## What the spike found

A throwaway spike on 2026-10-03 ran this on the Mac with QEMU 11.1.2 and hvf. Its scripts were not kept.

- The guest reaches the Mac's dev server at `10.0.2.2:8080` (HTTP 200 in 16 ms). With the kiosk pointed at it, the page
  renders in WPE with the fixture scan and the VM's own scan.
- A server bound only to the Mac's loopback is reached from the guest at `10.0.2.2` (HTTP 200) and is not reachable on
  the Mac's LAN address. `eclipse-mosquitto:2` is among Podman's local images; Mosquitto is not installed on the Mac.
- A QMP `screendump` takes 5 to 12 ms. A CSS edit is on the kiosk's screen after 1.8 to 3.4 s. A Kotlin edit took 9.5 s
  and 4.9 s in the first VM and 1.9 s twice in a later one; the cause of the spread is unknown.
- Provisioning the VM takes 2 min 36 s (three boots, cloud-init, the apt installs). A child qcow2 overlay of a
  provisioned, cleanly powered-off disk takes 0.03 s to create, is 196 kB, has ssh after 10 s, shows the page after about
  10 s, and cloud-init does not run again (`done`, no new apt history).
- With QEMU's own cocoa window the animations are smooth, and the page shows the visual defects of the panel (the user's
  observation).
- The remote inspector works, see below.

### The window

QEMU's window is the guest's pixels: 800 by 480 pixels is 400 by 240 points on a Retina display. Zoom To Fit scales it
but also reports the window size to the guest through the virtual GPU's EDID, so the guest's preferred mode follows the
window. The kiosk forces `COG_PLATFORM_DRM_VIDEO_MODE=800x480`; once the preferred mode changed, cog failed with
`Failed to initialize DRM`, at the next kiosk start. Three settings together keep the guest at 800 by 480 while the window
scales:

- `virtio-gpu-pci,xres=800,yres=480,edid=off`: the guest is not told the window size. Alone it leaves the guest with
  generic modes and no `800x480`.
- `video=Virtual-1:800x480M@60e` on the kernel command line, which adds the mode. The Pi's device file forces its panel
  the same way with `HDMI-A-1`.
- `-display cocoa,zoom-to-fit=on`, then a resize of the window to 800 by 512 points (the content plus the 32 point title
  bar) with `osascript`.

Checked: the framebuffer stays 800 by 480 (screendump), `800x480` is in the connector's modes, and a kiosk restart loads
the page, also after the window was resized to 1000 by 632. macOS keeps the aspect ratio of the content on resize.

### The remote inspector

`WEBKIT_INSPECTOR_HTTP_SERVER=127.0.0.1:2999` with `--enable-developer-extras=true` in `COG_ARGS` makes cog serve a page
that lists the inspectable pages and the Web Inspector itself. The WPE documentation names WebKitGTK-based and
Chromium-based browsers as clients and not Safari; Safari's Develop menu, which speaks Apple's own protocol, does not list
the device. The page opens as a normal tab in Safari and in Playwright's WebKit, which showed the live DOM of the kiosk's
page.

With the variable alone, cog does not start: the main thread waits in libproxy's `g_network_monitor_get_default` for a
`g_once`, and the `dconf worker` thread waits on a GObject type-class lock inside `g_bus_get_sync`. Headless, with the
display fixed, 3 of 3 starts hung. `GSETTINGS_BACKEND=memory` removes dconf and started 4 of 4 times;
`GIO_USE_NETWORK_MONITOR=base` and `GIO_USE_PROXY_RESOLVER=dummy` each started once. The kiosk does not use dconf. The
earlier note in the [page load spec](2026-10-03-page-load-design.md), that the server "accepted a connection but answered
nothing", was this hang.

## Design

### Parts and the two ways

| Part       | What                                                                              | Started by     |
|------------|-----------------------------------------------------------------------------------|----------------|
| Dev server | Gradle `jsBrowserDevelopmentRun --continuous` on `127.0.0.1:8081`                 | either way     |
| Broker     | Mosquitto with the board's configuration on `127.0.0.1:8080`, holding the fixture | either way     |
| Kiosk VM   | WPE in QEMU: window and Web Inspector                                             | `make preview` |

| Way     | Start                                                                                                            | Look at                                                                                          |
|---------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Browser | The run configuration `netmon-web-display [jsBrowserDevelopmentRun --continuous]`, which starts the broker first | `http://localhost:8081/` in any browser; the page's defaults find the broker on `localhost:8080` |
| Kiosk   | `make preview`, or a run configuration that calls it                                                             | The QEMU window, and the same URL in a browser                                                   |

A command starts what does not answer yet and stops only what it started, so the browser way and `make preview` can
run side by side.

The dev server moves from Gradle's default port 8080 to 8081 in [build.gradle.kts](../../../build.gradle.kts). The page
takes its broker from the host it was loaded from and port 8080, so with the broker on 8080 the browser needs no query
parameters.

### The broker

`make broker` runs Mosquitto from `eclipse-mosquitto:2` in a Podman container, with the board's
[mosquitto-netmon.conf](../../../packages/netmon-scanner/conf/mosquitto-netmon.conf) mounted read-only (a websocket
listener on 8080, anonymous), published on `127.0.0.1:8080` only. It then publishes the fixture as retained messages with
`podman exec … mosquitto_pub`, and runs until Ctrl-C, when it stops the container. A broker that already answers on the
address is used as it is, and left running at the end. A TCP connect cannot tell a broker from another server, so a
non-broker on that port shows as a failed connection in the page's console; if the port is taken when the container
starts, Podman's error is reported.

| Variable | Default          | Meaning                                                                                                                                                                                              |
|----------|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BROKER` | `localhost:8080` | `host:port` of the websocket broker. The default starts the container and publishes the fixture. Any other value starts and publishes nothing: `BROKER=netmon.local:8080` shows the board's own data |
| `SCAN`   | `14+39`          | Recent and stable hosts of the fixture; `14+39x2` publishes two scans                                                                                                                                |

The run configurations take both variables as environment variables. The page's `broker.host` and `broker.port` query
parameters keep working as they do.

### The command

`make preview` runs in the foreground for a development session and ends everything it started on Ctrl-C. The
orchestration is a Python module next to [vm_device.py](../../../tests/vm_device.py), run with `uv run --frozen` like the
other targets. It takes `BROKER` and `SCAN` and one more variable.

| Variable  | Default  | Meaning                                                                                                                                                                  |
|-----------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `INSPECT` | `Safari` | The application that opens the Web Inspector once the session is up; `INSPECT=0` opens nothing; any other application name works, for instance `INSPECT="Google Chrome"` |

It always prints the inspector's URL, `http://127.0.0.1:2999/`, whether or not `INSPECT` opens it.

### Three layers

1. **Base**: the testkit's cached Raspberry Pi OS image, `prepare.prepare()`.
2. **Provisioned**: the base after cloud-init has run for the sample device file rendered by `vm_device.py`, and after a
   clean guest `poweroff`. The page and the broker come from the Mac, so the preview's device file leaves out the netmon
   apt source and packages and keeps the kiosk; see Open. The layer lives in `~/.cache/pihero/preview/` as a read-only
   qcow2 with the boot partition image beside it. Its name is a hash of the base image's id and the rendered `user-data`.
   A new hash builds a new layer, the first `make preview` builds it (about 2.5 min), and older layers are left for the
   user to delete.
3. **Session**: a qcow2 overlay on layer 2 in a per-session directory under `dist/preview/`, deleted at the end.

### The session

1. Start the broker and the dev server if they do not answer.
2. Start QEMU on the session overlay with the display settings above and a port for ssh and one for QMP. Resize the
   window once ssh answers.
3. Write the session's settings into the guest's `/etc/pihero/kiosk.conf` and restart `pihero-kiosk`:
   `URL=http://10.0.2.2:8081/?broker.host=10.0.2.2&broker.port=8080`, `--enable-developer-extras=true` in `COG_ARGS`,
   `WEBKIT_INSPECTOR_HTTP_SERVER=127.0.0.1:2999` and `GSETTINGS_BACKEND=memory`. A loopback `BROKER` host is `10.0.2.2` to
   the guest; any other host is passed as it is. The overlay is deleted at the end, so nothing reaches the sample device
   file; the footprint spec keeps the inspector out of the sample on purpose.
4. Open an ssh tunnel from `127.0.0.1:2999` on the Mac to the guest's inspector.
5. Unless `INSPECT=0`, poll the inspector's page list until it names the kiosk's page, then run `open -a "$INSPECT" <url>`. The
   URL is the inspector itself, `Main.html?ws=…`, read from the Inspect button of the first target in the list; the list's
   own URL is used if the list holds no target after 30 s. The tab is opened once per session, not after each reload.
6. On Ctrl-C or SIGTERM: close the tunnel, `poweroff` the guest and wait for QEMU, stop what step 1 started, delete the
   overlay.

A session that was killed without cleanup leaves a record (the process ids and the container's name in the session
directory). The next `make preview` stops what it names, if it is still the same program, and removes the directory. A
second `make preview` while one runs fails and names the first.

### The fixture

`scans()` and its helpers move from [layout.py](../../../tests/layout.py) to a module that both the layout test and the
broker command import, so there is one definition. The display subscribes to scan events only, and the container
publishes websockets only, so the README's `mqtt pub` examples for host events do not reach it. A scan published with
`podman exec … mosquitto_pub` replaces the fixture by hand while the animations are watched; the README shows one.

`main.kt`'s unused `scan()` and the commented-out block that calls it are removed.

### The dev server

The dev server listens on the Mac's loopback only. It still has to accept the guest's `Host: 10.0.2.2:8081`
header, which webpack-dev-server rejects by default; the spike set `allowedHosts` to `all`. Whether a narrower value
works is untested, see Open. With `host` set, the dev bundle's live-reload client is built for that host, which is the
guest's own loopback in the VM and shows as "Trying to reconnect" on the kiosk's screen; `client.webSocketURL` set to
`auto://0.0.0.0:0/ws` makes the client use the address the page came from. A CSS edit then reaches the kiosk's screen
after 1.9 s.

### QEMU from the testkit

The testkit's `qemu_command` has `-display none` and the GPU settings fixed. The spike replaced the function in a script,
which the preview must not do. The display mode, the EDID setting and the extra kernel argument become parameters of
`pihero_testkit.vm` (a change in [pihero](https://github.com/bkahlert/pihero)), taken by a version bump of the pinned
testkit. The window resize is the preview's own: it needs the terminal to have Accessibility permission, which the spike
had.

### What else changes

- The run configuration `netmon-web-display [jsBrowserDevelopmentRun --continuous]` stays and runs the broker first.
  New run configurations call `make broker` and `make preview`.
- The README's "Run the web display component locally" describes the two ways.
- `make display` is removed. Nothing calls it: no CI job, test or target. The README mentions it once and the tier 2 spec
  and plan, which introduced it on 2026-10-01, describe it. The preview shows a VM's page better, and a browser shows a
  board's.
- `draft/`, the Fritz2 Tailwind prototype last touched in February 2024, and its run configuration `.run/draft.run.xml`
  are removed in a commit of their own. Nothing else refers to them.
- Gradle tasks stay what the Make targets call; they are no longer documented as entry points.

## Alternatives

| Alternative                                           | Why not                                                                                                                |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| Playwright's WebKit with the Pi's fonts as the loop   | The engine differs from WPE, see the `atan2` finding                                                                   |
| A VM that keeps running between sessions              | A lingering 1 GB VM is a risk the user rejected; the layers make a new one cheap                                       |
| `screendump` as the only view                         | It cannot judge the radar pulse or the glow                                                                            |
| Zoom To Fit alone                                     | The guest follows the window and the kiosk loses its mode                                                              |
| A VNC viewer for the scaled view                      | Not needed once the guest does not follow the window                                                                   |
| The VM's own Mosquitto as the only broker             | The browser way would need the VM running, which is the cost it avoids                                                 |
| A broker written in Python                            | It needs MQTT code of its own and takes no live `mqtt pub`, which the animations need; Mosquitto is the board's broker |
| A public test broker                                  | Shared and without a fixture                                                                                           |
| `GIO_USE_NETWORK_MONITOR=base` for the inspector hang | Works, but fixes the side that the kiosk uses; dconf is the side it does not                                           |

## Tests

- Tier 0 (`make test-tier0`): the `SCAN` and `BROKER` grammars; which `BROKER` values start the container; the `INSPECT`
  values and the command they give; the inspector URL read from a copy of the list page, recorded from the kiosk when the
  preview is built (the spike only printed it); the `kiosk.conf` text the session writes, with a loopback broker as
  `10.0.2.2`; the QEMU arguments of a preview session (the EDID setting, the kernel argument, the display); the layer
  name changes with the base id and the rendered `user-data` and with nothing else.
- A `preview` marker, local only because it needs QEMU, Podman and a window server, run by `make test-preview`: the broker
  holds the fixture as retained messages and answers a websocket handshake; a session's guest stays 800 by 480 after its
  window was resized and its kiosk restarted; a page served from the Mac's loopback loads in the kiosk, and the
  inspector's list, through the tunnel, names a target; the QEMU a killed preview left behind is ended by the next one.
- By hand: the kiosk shows the fixture's hosts from the dev server; the window opens at 800 by 480 points on the main display and keeps its aspect ratio; Ctrl-C leaves no `qemu`,
  `gradle` or Mosquitto container behind; the browser way and `make preview` run side by side.
- The layout test keeps passing on the shared fixture module.

## Open

- Settled: the preview's device file can leave out the netmon packages, but it must name `pihero-kiosk`. The sample gets
  the kiosk only as a dependency of `netmon-display`; without that, no `pihero-kiosk` unit exists and nothing shows.
  The debs from `dist/` are not needed.
- Settled: the container's Mosquitto reaches the guest. The kiosk shows the fixture from the broker at `10.0.2.2:8080`,
  and a websocket handshake to the published port answers `101` with the board's configuration.
- Whether `allowedHosts` can be narrower than `all`.
- Whether an open inspector survives the page's reloads and a kiosk restart. The target ids in the inspector's URL
  (`/socket/1/1/WebPage` in the spike, with one page) may change when the kiosk restarts; a dead tab then needs a reload of
  the list. The preview does not reopen it.
- The cost of the dev bundle in a no-JIT WPE. Not measured; the spike's CPU probe read the wrong process.
- The Kotlin edit time, 1.9 to 9.5 s, is unexplained and was not measured again.
- The VM runs at the Mac's speed. It shows what the panel draws, not how loaded the Pi Zero 2 W is.
- Whether anyone uses `make display` by hand. The repo shows no caller; the spec removes it.
- A first `make preview` on a machine without the cached base image also downloads it; not measured.

## Out of scope

Changing the sample device file, changing the production packages, and a faster Kotlin build.
