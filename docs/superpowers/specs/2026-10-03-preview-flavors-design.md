# The preview in three flavors

## Intent

Editing the display should show the result where it is judged, from one make target per place, with the same options in
each place, and with a Web Inspector on the page in each.

The [preview spec](2026-10-03-preview-design.md) gave two ways to look: the *browser* (fastest, the Mac's rendering) and
the *VM* (WPE in QEMU, exact rendering, the Mac's speed). This change adds the *device*: the page in the kiosk of a real
Pi, the slowest and the only one whose CPU use is the panel's. It also makes the three the same command with three names.

## Today

- `make preview` is the VM. The browser way is two commands (`make broker` and `./gradlew jsBrowserDevelopmentRun
  --continuous`) or the IDE's compound run configuration `netmon-web-display [jsBrowserDevelopmentRun --continuous]`.
- The device has no way: `make deploy` installs built packages, which is minutes per edit.
- `BROKER` defaults to `localhost:8080`, and that exact string means "start the container". Any other value means
  "use that broker". An address that already answers is used and left running, for the broker and for the dev server.
- The page asks for `stats.json` next to itself. The dev server has none, so the status bar's CPU figure is empty in the
  browser and in the VM. On the board lighttpd serves the sampler's file.

## Design

### Targets

| Target                         | Display                                                   |
|--------------------------------|-----------------------------------------------------------|
| `make preview-browser`         | A browser tab on `http://localhost:8081/`                 |
| `make preview-vm`              | The kiosk in a QEMU window                                |
| `make preview-device TARGET=…` | The kiosk of the Pi at `TARGET` (`user@host`, as `deploy`) |

`make preview` stays and is `preview-vm`. All three call `tests/preview.py --on browser|vm|device`; the Makefile has no
logic of its own. `make broker` stays for running only the fixture by hand.

All three need the dev server, and Gradle allows one build per project directory, so **only one flavor runs at a time**.
A second one fails and names the first (`a preview is already running (process N)`), as `make preview` does today. A
dev server that answers on 8081 without a preview owning it fails too: `end it first`. Nothing is reused implicitly.
This also lets the flavors share one record file, one inspector port and one fixture port. It replaces the
[preview spec](2026-10-03-preview-design.md)'s "the browser way and `make preview` can run side by side".

### Options

| Variable  | Default   | Meaning                                                                                       |
|-----------|-----------|-----------------------------------------------------------------------------------------------|
| `BROKER`  | `fixture` | Where the page gets its hosts, see below                                                      |
| `SCAN`    | `14+39`   | The fixture's hosts; `14+39x2` publishes two scans. Ignored unless `BROKER` is `fixture`      |
| `INSPECT` | `Safari`  | The application to open once the session is up; `0` opens nothing. Any application name works |
| `TARGET`  | none      | `preview-device` only: `user@host[:port]`. Required there, refused elsewhere                  |

| `BROKER`        | Meaning                                                                                                                                  |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------|
| unset, `fixture` | A Mosquitto container with the board's configuration on `127.0.0.1:8080`, holding the `SCAN` hosts. Started and stopped by the session. If 8080 is taken the session fails and names `BROKER=localhost:8080` as the way to use the broker there |
| `device`        | The board's own broker. Only `preview-device`; real hosts, no fixture, nothing started                                                    |
| `HOST:PORT`     | That broker, as it is. Nothing is started or stopped. `localhost` is the Mac in every flavor                                              |

Anything else fails in one line that names the three forms. The old meaning of the literal `localhost:8080` (start the
container) is gone; it now attaches to what is there.

What `INSPECT` opens depends on the flavor: the browser flavor opens the page, whose own developer tools are the
inspector; the VM and device flavors open the kiosk's Web Inspector (`Main.html?ws=…`) as they do today.

### One flavor, four steps

A flavor is the part of a session that differs. Everything else (the record, the fixture, the dev server, the inspector
wait, the `INSPECT` command, the ready message, the cleanup stack) stays in `preview.py` and is shared.

| Step              | Browser                      | VM                                                     | Device                                                         |
|-------------------|------------------------------|--------------------------------------------------------|----------------------------------------------------------------|
| Prepare           | nothing                      | layer, overlay, QEMU, window (as today)                | `ssh` to `TARGET`; check `pihero-kiosk` is installed           |
| Point at the page | nothing                      | `kiosk.conf` in the guest, restart                     | session drop-in on the board, restart, see below              |
| Inspector         | the page's own tools         | tunnel `127.0.0.1:2999` to the guest                   | tunnel `127.0.0.1:2999` to the board                           |
| Tear down         | nothing                      | tunnel, `poweroff`, delete the overlay                 | remove the drop-in, restart, close the tunnel                  |

### The device flavor

**Reaching the Mac.** One `ssh -N` carries all forwards: `-R 18081:127.0.0.1:8081` for the dev server,
`-R 18080:127.0.0.1:<port>` for a broker on the Mac (only then: the fixture, or a `HOST:PORT` whose host is a loopback name), and `-L 2999:127.0.0.1:2999` for the inspector. The Mac keeps
listening on loopback only, nothing is exposed on the LAN, and the board needs no route to the Mac. The reverse ports
are 18080 and 18081 because the board's own broker holds 8080 and its lighttpd holds 80. The kiosk's page is
`http://127.0.0.1:18081/?broker.host=127.0.0.1&broker.port=18080` with a broker on the Mac, `…broker.port=8080` with
`BROKER=device`, and the given host and port with any other `HOST:PORT`.

**Nothing persistent changes on the board.** The kiosk unit reads `Environment=URL=…`, then
`EnvironmentFile=-/etc/pihero/kiosk.conf`; a later `EnvironmentFile=` overrides an earlier one. The session writes the
board's `kiosk.conf` with the same changes the VM gets ([`session_kiosk_conf`](../../../tests/preview_kiosk.py): the URL,
developer extras in front of the board's own `COG_ARGS`, the inspector address, `GSETTINGS_BACKEND=memory`) to
`/run/netmon-preview/kiosk.conf`, and adds `/run/systemd/system/pihero-kiosk.service.d/preview.conf` with
`EnvironmentFile=/run/netmon-preview/kiosk.conf`. Both live on tmpfs. Ending the session removes them, runs
`daemon-reload` and restarts the kiosk; a reboot removes them too, so a crashed session cannot leave the panel on a dead
tunnel for long. While the tunnel is down the kiosk waits for its URL instead of crashing.

**The CPU figure.** The dev server proxies `/stats.json` to `http://<host of TARGET>/stats.json` in this flavor only, so
the status bar shows the board's real sampler. `webpack.config.d/dev-server.js` reads the address from an environment
variable the session sets for Gradle; without it nothing is proxied.

**The tunnel is watched.** The session asks every second whether `ssh -N` still runs. When it ends, the session prints
the tunnel's last message from `dist/preview/tunnel.log` (or its exit status) and ends like Ctrl-C, so the board is
restored instead of left on a dead URL. The tunnel uses IPv4 (`-4`): a tunnel over the Mac's IPv6 path died when that
path dropped, and `ssh -v` then showed `Network is unreachable` for the IPv6 address while IPv4 worked.

**Dead tunnels leave the board's ports taken.** The board's `sshd` keeps `18081` and `18080` until it notices that the
Mac is gone, which a broken path never tells it. Before it opens a tunnel, the session ends every `sshd` process that
listens on those two ports. Two Macs cannot preview on the same board at once.

**Recovery.** The record gains the board's `TARGET` and the tunnel's process id. After a killed session, the next start
ends the tunnel, and removes the drop-in and restarts the kiosk if the board answers over ssh. If it does not, the
reboot is the fallback and the message says so.

### Run configurations

`preview.run.xml` stays and calls `make preview`. New shell configurations call `make preview-browser`,
`make preview-vm` and `make preview-device`. The compound configuration `netmon-web-display
[jsBrowserDevelopmentRun --continuous]` is replaced by `preview-browser`. `netmon-web-display dev server` stays as the
IDE's way to debug the Kotlin/JS build.

### The README

"Run the web display component locally" describes the three targets, the four variables and the `BROKER` values in one
table, and says that only one runs at a time.

## Alternatives

| Alternative                                         | Why not                                                                                                             |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| One target with `MODE=browser\|vm\|device`          | One line in `make help`; every run configuration needs an environment variable                                      |
| `TARGET` alone chooses (`preview` / `TARGET=…`)     | The browser flavor still needs a name, so the scheme splits as soon as it is written down                           |
| Edit `/etc/pihero/kiosk.conf` on the board          | A crash leaves the panel on a dead URL until someone repairs the file                                               |
| The Mac's dev server on the LAN                     | Exposes the dev server and the fixture to the network; the board then needs the Mac's address                       |
| `BROKER=localhost:8080` for the board's own broker  | The same string means the Mac in the other flavors and is the default                                               |
| Reuse a running broker or dev server on its own     | Hidden behaviour: who stops it, and what the second flavor shows when the first ends. `HOST:PORT` says it explicitly |

## Tests

- Tier 0: the `BROKER` grammar (the three forms, and the one-line failure for others, for `device` outside
  `preview-device`, and for `TARGET` outside it or missing in it); the page URL per flavor and broker; the drop-in and
  `kiosk.conf` text for the board, with the board's own `COG_ARGS` kept; the `ssh` argument list of the device flavor;
  the recovery actions; the failure when something answers on 8081 or 8080; `make preview` being `preview-vm`.
- `make test-preview` keeps covering the fixture and the VM session. The browser flavor adds: the fixture holds the
  hosts and the dev server's page loads, if Gradle is free.
- By hand, on the board: the page shows the fixture's hosts; a CSS edit reaches the panel; the Web Inspector shows the
  live DOM; the status bar shows the board's CPU; Ctrl-C leaves `/run/netmon-preview` and the drop-in gone and the kiosk
  back on its own page; `kill -9` of the session and the next start recovers. The device flavor cannot run in CI.

## Open

- Settled: the board's `sshd` allows the remote forwards. On a Pi with the sample device file, `18081` and `18080` listened
  on the board's loopback during a session and were gone after it.
- Settled: Gradle's webpack task sees `NETMON_STATS_PROXY`. Against a stand-in on 127.0.0.1:8099, the dev server answered
  `/stats.json` with the stand-in's file. Without a reachable target the proxy answers 504, not 404.
- The dev bundle costs more CPU and memory than the production one, and the kiosk unit has `MemoryMax=300M`. The device
  flavor shows a relative figure; absolute numbers still need `make deploy`.
- The reverse tunnel adds the board's `sshd` to the CPU the board spends. It does not count in the kiosk's cgroup.
- Settled: the inspector's page list on the board names a target (`Main.html?ws=…`), as in the VM.
- Settled: the status bar's CPU figure is the board's own. `/stats.json` through the dev server returned the sampler's
  numbers (`kioskCpu` 62 with the dev bundle running).
- Settled, by a run on a real board: ready in 40 to 60 s; a CSS edit reached the board's dev server within seconds; Ctrl-C
  left no `/run/netmon-preview`, no drop-in and the kiosk on its own `URL`; `kill -9` of the session left the container,
  the tunnel and the board's drop-in, and the next start ended and removed them; `BROKER=device` gave `broker.port=8080`
  and started no container; `BROKER=localhost:8080` next to `make broker` forwarded `18080`, used the running container
  and left it running.
- Not seen by eye: the panel's pixels and the Web Inspector in Safari (all runs used `INSPECT=0`).
- One run of `BROKER=device`, seconds after the end of another session, failed with `the ssh tunnel … did not come up
  within 15 s`; the tunnel log was empty and the next run passed. A login to an idle board takes about 3 s, and the board
  restarts its kiosk at the end of a session. Cause not proven; if it recurs, raise the 15 s in `Board.open_tunnel`.
- The board's kiosk restarts twice per session (start, end): the panel is blank for the length of the restart.

## Out of scope

Serving the production bundle to the device (`BUNDLE=production`), a flavor for another device, changing the production
packages or the sample device file.
