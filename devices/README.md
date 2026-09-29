# Device files

`sample/` is a complete Netmon device for [Pi Hero 2](https://github.com/bkahlert/pihero): pihero's sample device plus the
netmon apt source (its key inline, so the device trusts nothing else), the packages `netmon-scanner` and `netmon-display`,
and `/etc/pihero/kiosk.conf` with the page the kiosk shows. Every other key is explained in pihero's
[devices/README.md](https://github.com/bkahlert/pihero/blob/main/devices/README.md). Directories other than `sample/`
are gitignored: keep your own here or in a private repository.

Copy the two files, set the hostname, your SSH public key, the pretty name, the USB gadget's name and subnet, and your
Wi-Fi in `network-config`; then, in a pihero checkout, `make flash DEVICE=<path to your directory> DISK=diskN`. First
boot takes a few minutes and reboots twice; then the panel shows the network, and any browser on the LAN shows it at
`http://<host>.local/` (the page subscribes to the broker on the host it was loaded from; `?broker.host=…&broker.port=…`
overrides that).

Two `runcmd` lines are specific to the display: `video=HDMI-A-1:800x480M@60e` forces the connector on for a panel that
reports no EDID (the HAMTYSAN 7-inch; drop the line for a display that does), and `cgroup_enable=memory` turns on the
memory controller Raspberry Pi OS boots without, so the units' `MemoryMax=` binds. `COG_PLATFORM_DRM_VIDEO_MODE` in
`kiosk.conf` picks the mode when the connector offers several.

The scanner's overrides go into `/etc/netmon/scanner.conf` (`BROKER_HOST`, `BROKER_PORT`, `NMAP_DATA_DIR`,
`JAVA_TOOL_OPTIONS`, and the `NETWORK_MIN_HOST_BITS`/`NETWORK_MAX_HOST_BITS` filter), written with `write_files` like
the kiosk's. A network the board reaches over Wi-Fi and a cable at once is scanned once, over the cable.
