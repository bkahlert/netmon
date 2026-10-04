# Device files

`sample/` is a complete Netmon device for [Pi Hero 2](https://github.com/bkahlert/pihero): pihero's sample device plus the
netmon apt source (its key inline, so the device trusts nothing else), the packages `netmon-scanner`, `netmon-display` and
`netmon-metrics` (optional: without it the status bar shows no figures), and `/etc/pihero/kiosk.conf` with the page the
kiosk shows. Every other key is explained in pihero's
[devices/README.md](https://github.com/bkahlert/pihero/blob/main/devices/README.md). Directories other than `sample/`
are gitignored: keep your own here or in a private repository. `sample/` is also what tier 2 boots: `make test-tier2`
renders it for the VM with the testkit's user and the local package repository and nothing else, so the file users copy is
the file that is tested.

Copy the two files, set the hostname, your SSH public key, the pretty name, the USB gadget's name and subnet, and your
Wi-Fi in `network-config`; then, in a pihero checkout, `make flash DEVICE=<path to your directory> DISK=diskN`. First
boot takes a few minutes and reboots twice; then the panel shows the network, and any browser on the LAN shows it at
`http://<host>.local/` (the page subscribes to the broker on the host it was loaded from; `?broker.host=…&broker.port=…`
overrides that).

Two `runcmd` lines are specific to the display: `video=HDMI-A-1:800x480M@60e` forces the connector on for a panel that
reports no EDID (the HAMTYSAN 7-inch; drop the line for a display that does), and `cgroup_enable=memory` turns on the
memory controller Raspberry Pi OS boots without, so the units' `MemoryMax=` binds. `COG_PLATFORM_DRM_VIDEO_MODE` in
`kiosk.conf` picks the mode when the connector offers several.

Three more `kiosk.conf` lines shrink the kiosk for a board with 415 MB. `COG_ARGS` gives cog the document-viewer cache
model, a 200 MB memory limit above half of which WebKit drops its caches and JavaScript code on every ten-second check,
and a relaunch if the web process dies. `JSC_useJIT=false` switches the JavaScript compiler off, where most of the saving
comes from, and `WEBKIT_SKIA_CPU_PAINTING_THREADS=1` paints with one thread. On the sample's board the kiosk's peak fell
from 235 to 160 MB of RAM and zram with the panel looking the same.

The scanner's overrides go into `/etc/netmon/scanner.conf` (`BROKER_HOST`, `BROKER_PORT`, `NMAP_DATA_DIR`,
`NETMON_SCANNER_OPTIONS` (the native image's runtime options, `-Xmx48m` by default), and the
`NETWORK_MIN_HOST_BITS`/`NETWORK_MAX_HOST_BITS` filter), written with `write_files` like the kiosk's. A network the
board reaches over Wi-Fi and a cable at once is scanned once, over the cable.
