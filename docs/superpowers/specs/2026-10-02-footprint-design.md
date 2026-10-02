# The footprint: apt next to the live stack on a 512 MB board

Date: 2026-10-02. Status: approved design, ready for planning. Continues follow-ups 2 and 3 of
[the tier 2 design](2026-10-01-tier2-vm-end-to-end-design.md).

## Intent

The scanner and the kiosk need about 600 MB on a board with 415 MB. Raspberry Pi OS swaps the difference into zram,
the kernel evicts the shared libraries of WebKit and the JVM and reads them back from the SD card, and apt has no memory
next to the two. The board's device file therefore stops both units around every dpkg run with an apt hook, the fleet
playbook stops them before every update, and the runbook tells everyone to stop the stack before `apt`. The sample
device file in [devices/sample/user-data](../../../devices/sample/user-data) deliberately has no such hook.

The finish line: `apt-get update` followed by `apt-get install --reinstall` of a small package runs on the live board
with the scanner and the kiosk running, without a stop, without thrash, without the hardware watchdog resetting the
board. Then the hook leaves the board's device template, the playbook exception goes, and the runbook's tripwire is
rewritten with the measured numbers.

Three decisions frame the work. The finish line stands, so the heavy levers are planned now rather than after a first
round: the scanner becomes a GraalVM native image and the page gets calmer. The scanner package becomes arm64-only,
since a native image exists for no 32-bit ARM. And the kiosk keeps cog and the page; a native renderer for the panel is
named as the escalation if this design still falls short, not designed here.

Out of scope: the display's rendering differences between cog and WebKit, the `MQTT::Disconnected` header, weekly CI,
and any change to pihero.

## Numbers

Board, 2026-10-02 07:40, two and a half days of uptime, in MB:

| Unit | RAM | zram | total |
|---|---|---|---|
| `netmon-scanner` | 35 | 77 | 112 |
| `pihero-kiosk` | 62 | 194 | 256 |
| both units | 97 | 271 | 368 |
| everything else | about 75 | about 80 | about 155 |

Load was 6.8 with `kswapd0` at 76 % of a core and 44 % iowait. The iowait is the decisive observation: the swap device
is zram in RAM, so iowait can only come from the SD card, which means the kernel is evicting file-backed pages, the
libraries, and reading them back. The budget therefore has to keep hot code resident, not only keep anonymous memory
under the RAM size.

VM, tier 2, 1 GB and no swap: the scanner's `MemoryCurrent` grows from 51 MB after the first scan to 148 MB as the heap
fills its `-Xmx128m`; the kiosk's `MemoryPeak` is 315 MB and its `MemoryCurrent` 227 to 241 MB. That is what the page
wants without any pressure.

The budget: the two units' working sets, anonymous plus file-backed, must fit into the RAM size minus everything else
minus apt's own peak minus a 40 MB margin. Everything else comes from the soak as used memory minus the two units;
apt's peak comes from the apt probe in the VM. Both are measured in step 1 of the order of work and written into this
section. Judging by today's numbers the two units have to land near 150 to 200 MB, half of what they take. The
scanner's share is its measured steady state after step 3; the kiosk gets the remainder, and the web process's share of
that is what its memory limit is sized from.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Measurement | A soak and an apt probe as opt-in tests in the testkit, markers `soak` and `apt`, never part of `installed or boot` | The display test and the memory lines already run against the VM and the board over the same harness; every lever is judged by the same numbers in the VM first and on the board second |
| What the soak asserts | Only invariants: both units active, restart counts and boot id unchanged, no OOM kill in either cgroup | A threshold on a number means nothing before the numbers exist; the table is the result |
| The apt probe's victim | A reinstall of `netmon-display` | Ours, small, and its postinst only restarts lighttpd, so a passing probe proves apt and dpkg next to the stack without touching either unit |
| The hook during the probe | The probe refuses to run while `/etc/apt/apt.conf.d/52netmon-dpkg` exists | The hook would stop the units and make the result meaningless |
| Scanner runtime | GraalVM native image, built by the `native-image` command on the shadow jar inside a container, not by the Gradle plugin | The plugin needs Gradle's Java plugin, which cannot be applied in the Kotlin Multiplatform module; with the slim classpath below the plugin's main value, the reachability metadata repository, is not needed |
| Native image distribution | GraalVM Community 25 from `ghcr.io/graalvm/native-image-community`, pinned by digest; Mandrel 21 as the fallback | Both ship linux-aarch64 and need no license; Community 21 ended in January 2024 |
| CPU target | `-march=compatibility` | GraalVM for JDK 24 and later targets ARMv8.1 by default; the Zero 2 W's Cortex-A53 is ARMv8.0 |
| Heap | A build-time default cap of 64 MB, a runtime `-Xmx48m` in the unit through an options variable, the effective maximum logged at startup | The default maximum heap of a native image is 80 % of RAM; the log line gives tier 1 the same proof the picked-up Java options gave |
| MQTT client | Eclipse Paho MQTT 3, in-memory persistence, automatic reconnect; `tcp://` by default, `ws://` for ports 8080 and 8081 as today; the unit's default broker port becomes 1883 | HiveMQ brings netty, RxJava, Dagger and JCTools, which need pins, substitutions and build-time initialization lists under native image; Paho has no transitive dependencies; the broker's local listener is on the same host |
| Logging | slf4j-simple replaces logback and the logstash encoder; the verbosity tables become the simple logger's per-logger level properties | Joran's reflection and Jackson 3 leave the image; structured arguments only ever rendered through the pattern; runtime level changes are not used |
| nmap output | A StAX parser over nmap's XML yields the hosts directly | Removes the Python child process per scan and the `python3` dependency, and the JSON model that existed only for the converter's output |
| Scan cadence | The pause between scans defaults to 30 s instead of 10 s, overridable as before | nmap runs a third as often; a change still shows within a minute, under the display's two-minute dated threshold |
| Kiosk knobs | Cog arguments through `COG_ARGS` and JavaScriptCore and Skia variables, written into `/etc/pihero/kiosk.conf` by the sample device file | WPE 2.48.3 reads no environment variable for memory limits; the limits are reachable only through cog's flags, and pihero's launcher passes `COG_ARGS` to cog |
| Remote inspector | Switched on by hand in a kept VM when a JavaScript heap figure is wanted, never in the sample | The file users copy is the file tier 2 boots |
| Page | Auto-reload polls once a minute; the radar icons pulse for ten seconds after a scan arrives; cards in the stable section take a one-minute clock; unused Tailwind plugins, prototyping CSS and the headless module go | The feature that reloads the panel after a display upgrade stays, at a twelfth of the requests; the infinite animation goes; the per-second work shrinks to the cards whose texts change; MQTT.js stays as documented |
| Package | `netmon-scanner` is `arch: arm64`, depends on nmap and mosquitto, ships the binary at `/usr/lib/netmon/netmon-scanner` | No JRE, no Python |
| The testkit | Unchanged, pinned at v2.4.0 | Its build module names every deb `_all.deb` and reuses self-built debs; both are harmless here since apt-ftparchive reads the control file and the deploy filter accepts the suffix, and the native build stays out of the testkit's self-build path |
| CI | The tier 1 matrix and the release lose `linux/arm/v7` and the QEMU setup that existed for it | The scanner no longer installs there |
| Version | The release that ships the native scanner is 2.0.0 | Dropping 32-bit ARM and the JRE dependency is a breaking change |
| Gate | After the board soak of step 3 and the A/Bs of step 4, the numbers go against the budget before the finish line is attempted | Short means the table names the side that is over and the escalation is a new brainstorm, not a longer list of knobs |

## Commands

```shell
make build                                   # Gradle, the native binary in a container, then nfpm: dist/*.deb
make soak                                    # boot the VM and sample both units for ten minutes: dist/tier2/soak.md
make soak TARGET=pi@netmon.local             # the same against the board: dist/ssh/soak.md
make apt-probe                               # apt next to the stack in the VM, apt's peak for the budget
make apt-probe TARGET=pi@netmon.local        # the finish line
make vm                                      # keep a VM for A/Bs: edit /etc/pihero/kiosk.conf there, restart, soak
uv run pytest -m soak --target=ssh --target-uri=pi@netmon.local --soak-duration=15m
uv run pytest -m apt --target=ssh --target-uri=pi@netmon.local --apt-timeout=600
```

## Components

### The soak

[tests/test_soak.py](../../../tests/test_soak.py), marked `soak`. The root [conftest.py](../../../conftest.py) registers
the marker and two options, the duration and the interval, defaulting to ten minutes and thirty seconds. The test runs
against the VM and the board and skips on podman, like `boot`.

Each sample reads:

- For both units: `MemoryCurrent`, `MemorySwapCurrent`, `MemoryPeak`, `MemorySwapPeak`, `NRestarts` and `ActiveState`
  from `systemctl show`, and `anon` and `file` from the unit cgroup's `memory.stat`.
- For `WPEWebProcess`: `Private_Dirty` and `Swap` from its `smaps_rollup`, since WebKit's own memory pressure handler
  measures exactly that and does not count swapped pages.
- System-wide: `MemAvailable` and `SwapFree` from `/proc/meminfo`, the load average, `pswpin`, `pswpout` and
  `pgmajfault` from `/proc/vmstat` as deltas, `/proc/pressure/memory` where the kernel has it, `mm_stat` of `zram0`
  where it exists, and the top CPU consumers from `top -bn1`.

Once per run it reads each unit cgroup's `memory.max`, so a board whose memory controller is off is noticed rather than
measured wrongly. Raspberry Pi OS boots with the controller off; the sample device file turns it on through
`bootconfig`, and the boot test asserts the kernel line.

Assertions are invariants only: both units active, the kiosk only where `/dev/dri` exists; `NRestarts` and
`/proc/sys/kernel/random/boot_id` unchanged from the first sample to the last; no increase of `oom_kill` in either
unit's `memory.events`. Everything else is reported: a Markdown table in `dist/tier2/soak.md` for the VM and
`dist/ssh/soak.md` for the board, following the display test's convention, and one summary line in the pytest output
the way the boot test prints the memory lines today.

### The apt probe

[tests/test_apt.py](../../../tests/test_apt.py), marked `apt`, with the options `--apt-timeout`, default 300 seconds,
and `--apt-package`, default `netmon-display`. It runs against the VM and the board and skips on podman. It is not
marked `mutating`, because the plugin skips `mutating` tests on the board and the board is where the probe matters.

The probe fails early with a message while the hook file exists on the target. It records the boot id and both
restart counts, then runs `apt-get update` and reinstalls the package inside a transient unit with memory accounting that
remains after exit, so that `MemoryPeak` and the exit status can be read from `systemctl show` afterwards and the unit
is stopped and reset. While apt runs, a thread samples both units every ten seconds with the soak's reader.

It asserts: apt exited zero within the timeout; the boot id is unchanged; both units are active with unchanged restart
counts. It reports apt's peak, the wall time, the major-fault delta and the memory pressure during the run. In the VM
this yields apt's peak for the budget. On the board it is the finish line.

### The scanner on the JVM

Four changes that stay testable on the JVM and precede the native image:

- **nmap's XML in-process.** `NmapNetworkScanner` hands nmap's `-oX -` output to a StAX parser that yields `Host`
  instances: the status, the first IPv4 or IPv6 address, the MAC address's vendor, the hostname. `NmapOutput`, its
  unwrapping serializer, `XmlToJsonConverter`, `xml2json.py`, its tier 0 test and the `python3` dependency go. The
  parser's tests feed it the XML the current JSON fixtures were converted from, plus a host without a name, one without
  a MAC, an IPv6 host, a down host and an empty run.
- **Paho.** `MqttPublisher` keeps its constructor and `Publisher` contract on Paho's MQTT 3 client with
  `MemoryPersistence`, automatic reconnect, clean session, and QoS 1 retained publishes. The sealed generic client over
  MQTT 3 and 5 goes; only MQTT 3 was used. The unit's `BROKER_PORT` default becomes 1883. The dependency check at
  planning time confirms that Paho brings nothing else onto the classpath.
- **slf4j-simple.** `Verbosity` and `Debug` set `org.slf4j.simpleLogger.log.<logger>` and the default level as system
  properties before the first logger is created; `LoggingSettings.apply` stays the entry point. The `kv` and `v`
  structured arguments become plain `{}` arguments. The integration tests parse the simple logger's lines instead of
  JSON lines. `Logback.kt` goes.
- **Hygiene.** `Pid` reads `ProcessHandle.current().pid()` without reflection. `ScannerSettings.pauseDuration` defaults
  to 30 s.

### The native image

The Containerfile and a build script live in `packages/netmon-scanner/native/`, a subdirectory, because the testkit
treats a package directory with a `build` file next to a `Containerfile` as self-building. The image starts from
`ghcr.io/graalvm/native-image-community:25` pinned by digest and is built with `--platform linux/arm64` always,
tagged by the Containerfile's digest the way the testkit tags its images. The script runs `native-image` on
`build/libs/netmon-all.jar` and writes `build/native/netmon-scanner`; it sizes the builder's heap from the container's
memory and prints the build's peak.

Build arguments ride in the jar under `META-INF/native-image/com.bkahlert.netmon/netmon-scanner/`: `--no-fallback`,
`-march=compatibility`, `-R:MaxHeapSize=64m`, and a resource configuration that includes
`assets/device-model-codes.json` and JmDNS's property files. The unreferenced `sfsymbols5` directory is not included.
Reflection metadata is expected to be empty with this classpath. If tier 1 or tier 2 shows a missing-metadata failure,
the tracing agent in the same container, run with the agent library against a broker, is the tool that produces it.

The unit runs `ExecStart=/usr/lib/netmon/netmon-scanner $NETMON_SCANNER_OPTIONS` with
`Environment=NETMON_SCANNER_OPTIONS=-Xmx48m`, overridable in `/etc/netmon/scanner.conf` as `JAVA_TOOL_OPTIONS` was.
`Application.start` logs the effective maximum heap in its configuration block. `MemoryMax` is set from the soak as a
leak guard, two to three times the steady state. `AmbientCapabilities` stays: nmap inherits the capabilities across
`execve` from a native binary as it did from the JVM.

Tests per tier: the installed tests check the package without a JRE and Python, the binary's start, the broker
connection over 1883, the logged heap cap and the capabilities; tier 2 proves that a scan completes and is published,
that the MAC prefixes download over https, and that the page shows the gateway; the board proves the CPU target.
The VM under hvf runs on the Mac's CPU and cannot catch an ARMv8.1 binary; the tcg VM emulates a Cortex-A72 and
can.

### The kiosk configuration

The sample device file writes these lines into `/etc/pihero/kiosk.conf`, tried in this order, each as an A/B in the kept
VM judged by the soak and `kiosk.png`, then confirmed on the board:

1. `COG_ARGS` with `--doc-viewer`, which selects the document-viewer cache model and switches off the web process's
   memory cache; `--web-mem-limit=<MiB>` with `--web-check-interval=10`, the limit sized from the first soak so that
   WebKit's strict threshold, half the limit, sits at the web process's share of the budget; and `--web-kill-threshold`
   with `--webprocess-failure=restart` as the leak guard. Without a limit cog ignores the other memory flags.
2. `JSC_useDFGJIT=false` and `JSC_useFTLJIT=false` first, then `JSC_useJIT=false`, which puts JavaScriptCore into its
   reduced-memory mode without generational GC. `JSC_logGC=1` once, to see in the journal that the environment reaches
   the web process.
3. `WEBKIT_SKIA_CPU_PAINTING_THREADS=1`. `WEBKIT_SKIA_ENABLE_CPU_RENDERING=0`, GPU painting, is a board-only A/B
   with a screenshot pair, since the VM paints in software anyway.

A new boot test proves the plumbing: cog's command line in `/proc/<pid>/cmdline` carries the configured arguments, and
`WPEWebProcess`'s environment carries the `JSC_` entries.

The remote inspector, `WEBKIT_INSPECTOR_HTTP_SERVER=<ip>:<port>` plus `--enable-developer-extras=true` in `COG_ARGS`,
is set by hand in a kept VM and opened with Playwright's WebKit when a heap figure is wanted.

### The page

- `AutoRefresher` takes its interval as a constructor argument, default one minute.
- A pure function maps the time since the last scan to the radar icons' class: the animation for ten seconds, the
  resting colour afterwards, the dated colour after two minutes as today.
- `hosts()` takes the clock as a parameter; the stable section gets a `CurrentTimeStore` ticking once a minute, the
  recent section keeps the second.
- `@tailwindcss/typography`, `tailwind-heropatterns`, the prototyping blocks in `utils.css` and `dev.fritz2:headless`
  leave the build; the one attribute name taken from headless becomes a literal.

The display test's selectors, `.host[data-status="up"]` and `.font-mono`, stay, so the test guards every page change.
MQTT.js stays. Leaner cards in the stable section are a visible change held back unless the DOM shows up as the memory
in the measurements.

### Packaging and CI

[packages/netmon-scanner/nfpm.yaml](../../../packages/netmon-scanner/nfpm.yaml): `arch: arm64`, `depends` nmap and
mosquitto, `contents` with the binary at `/usr/lib/netmon/netmon-scanner`, mode 0755, in place of the jar. The
description no longer says JVM.

[Makefile](../../../Makefile): a file rule makes `build/native/netmon-scanner` depend on `build/libs/netmon-all.jar`,
so `make build` rebuilds the binary only when the jar changed; `gradle` runs it after the Gradle tasks; `clean` already
removes `build`.

[.github/workflows/ci.yml](../../../.github/workflows/ci.yml) and
[release.yml](../../../.github/workflows/release.yml): the tier 1 matrix is `linux/arm64` only, the
`docker/setup-qemu-action` steps go. Every job that runs `make build` or `make gradle` builds the binary on the arm
runner.

[tests/test_static.py](../../../tests/test_static.py) stubs `/usr/lib/netmon/netmon-scanner` instead of `/usr/bin/java`
for `systemd-analyze verify`; shellcheck covers the native build script; the cloud-init schema check covers the new
kiosk lines.

### The board and choam.de

The board gets the new scanner by `make deploy` and the kiosk lines by editing `/etc/pihero/kiosk.conf` with a kiosk
restart, no reflash. The finish line moves the hook file aside for the probe and puts it back if the probe fails; that
is the one mutation of the live board in this work and is confirmed with the user first.

After a green probe, in the choam.de repository: the device template loses the hook entry and gains the kiosk and
scanner lines; the host variables lose `apt_stop_units`; the runbook's Netmon row describes a binary without a JRE,
its memory section carries the before-and-after table, the "stop the stack before `apt`" tripwire becomes a note with
the measured headroom and apt's timing, and the stop line leaves the command block. The zram tripwire stays.

System levers observed in the soak and decided in the runbook, not here: removing the armhf foreign architecture if no
armhf package is installed, which halves apt's list download and cache build; `tailscaled`'s CPU and memory; the zram
size once the working set fits. The kiosk's `MemoryMax=300M` is pihero's and stays; the web process kill threshold is
the finer guard.

### Make and documentation

`Makefile` gains `soak` and `apt-probe`. The README's install section says arm64; its build section names the native
step and podman. [devices/README.md](../../../devices/README.md) swaps `JAVA_TOOL_OPTIONS` for `NETMON_SCANNER_OPTIONS`
and names the kiosk lines. This spec's Numbers section receives the budget and the before-and-after table.

## Order of work

One pull request per step, every tier green at every step.

1. **Harness.** The soak, the apt probe, the make targets. The first VM soak and the VM apt peak give the budget, written
   into Numbers. The board baseline soak, which needs the board's SSH key unlocked in KeePassXC.
2. **Scanner on the JVM.** The StAX parser, Paho, slf4j-simple, the process id, the cadence. Still an all-architecture
   jar.
3. **Native image.** Container build, Makefile rule, package architecture, unit, static checks, CI matrix. Deploy to the
   board, the installed tests over SSH, soaks in the VM and on the board.
4. **Kiosk.** The configuration lines in the sample and the page changes, each A/B in the kept VM, then the board's
   configuration by hand.
5. **Finish line.** The gate: the numbers against the budget. Then the hook aside, the apt probe timed. Green: the
   choam.de edits, release 2.0.0, and the board upgraded from the repository next to the live stack, the first real use
   of the new path. Short: the table names the side that is over, and the escalation is a new brainstorm.

## Failure modes

- The native build runs out of memory in the podman machine, 3.8 GB today: the script reports the build's peak; raising
  the machine's memory is the user's decision.
- The binary dies with an illegal instruction on the Cortex-A53: the unit fails at first start after `make deploy`,
  which points at the CPU target; the tcg VM reproduces it.
- Missing reflection or resource metadata: a runtime exception in tier 1 or tier 2, not a build error; the tracing
  agent produces the entry.
- JavaScriptCore without its JIT is too slow for the page: the display test's wait for the gateway catches it, and the
  JIT step is dropped.
- The web process kill threshold is set too low: the kiosk reloads in a loop, visible as restarts and a dark panel; the
  threshold goes back up.
- The apt probe fails: by time, by a reset, or by a stopped unit, each reported distinctly, so the runbook learns which.
- The soak's invariants fail on the board without any change of ours: the baseline is what it is and the number stands
  in the table.

## Follow-ups, in order

1. **Weekly tier 2 CI** under software emulation, follow-up 4 of the tier 2 design.
2. **The testkit names debs by their architecture**, a one-line change in pihero's build module, when a pihero release
   is next due.
3. **The native build as a CI artifact** shared between jobs, if the per-job build time hurts.
4. **The display's rendering differences** seen in tier 2, and the `MQTT::Disconnected` header.

## Sources

Verified for this design on 2026-10-02 from the WPE WebKit 2.48.3 release tarball, cog 0.18.4, and the GraalVM
documentation. Trixie ships `libwpewebkit-2.0-1` 2.48.3-1 and cog 0.18.4-1.

- WebKit's periodic memory pressure handler, its footprint metric and thresholds:
  `Source/WTF/wtf/MemoryPressureHandler.cpp` and `Source/WTF/wtf/linux/MemoryFootprintLinux.cpp` in the tarball at
  <https://wpewebkit.org/releases/wpewebkit-2.48.3.tar.xz>; the UI process monitor polling `/proc/meminfo` and the
  cgroup: <https://github.com/WebKit/WebKit/blob/wpewebkit-2.48.3/Source/WebKit/UIProcess/linux/MemoryPressureMonitor.cpp>.
- The environment variables WPE reads: `Source/WebKit/glib/environment-variables.md.in` in the tarball; the downstream
  variables that do not exist upstream: <https://github.com/Igalia/cog/discussions/724>.
- JavaScriptCore's `JSC_` options: `Source/JavaScriptCore/runtime/Options.cpp` and `OptionsList.h` in the tarball.
- Skia CPU painting and its thread variables: `Source/WebKit/WebProcess/glib/WebProcessGLib.cpp` and
  `Source/WebCore/platform/graphics/skia/SkiaPaintingEngine.cpp` in the tarball.
- Cog's memory flags and `--doc-viewer`: <https://github.com/Igalia/cog/blob/0.18.4/launcher/cog-launcher.c>.
- Raspberry Pi OS and the memory controller: <https://github.com/RPi-Distro/pi-gen/issues/917>.
- No 32-bit ARM in native image:
  <https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/Platform.java>;
  the ARMv8.1 default and `-march=compatibility`:
  <https://github.com/oracle/graal/blob/master/substratevm/CHANGELOG.md> and
  <https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/OptimizationsAndPerformance.md>;
  the Cortex-A53: <https://www.raspberrypi.com/documentation/computers/processors.html>.
- Native image memory management, the 80 % default and `-R:MaxHeapSize`:
  <https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/MemoryManagement.md>; build resources:
  <https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md>; the tracing agent:
  <https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/>.
- The Gradle plugin's dependence on the Java plugin and its incompatibility with Multiplatform modules:
  <https://github.com/graalvm/native-build-tools/blob/master/native-gradle-plugin/src/main/java/org/graalvm/buildtools/gradle/NativeImagePlugin.java>
  and <https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html>.
- Distributions: <https://github.com/graalvm/graalvm-ce-builds/releases> and <https://github.com/graalvm/mandrel/releases>.
- HiveMQ's client under native image, the precedent and its cost:
  <https://github.com/hivemq/mqtt-cli/blob/master/build.gradle.kts>,
  <https://github.com/hivemq/hivemq-mqtt-client/issues/467>, <https://github.com/hivemq/hivemq-mqtt-client/issues/578>;
  the reachability metadata repository: <https://github.com/oracle/graalvm-reachability-metadata>.
- Paho: <https://github.com/eclipse-paho/paho.mqtt.java>, release 1.2.5.
