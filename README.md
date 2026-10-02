# Netmon [![License](https://img.shields.io/github/license/bkahlert/netmon?color=29ABE2&label=License&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA1OTAgNTkwIiAgeG1sbnM6dj0iaHR0cHM6Ly92ZWN0YS5pby9uYW5vIj48cGF0aCBkPSJNMzI4LjcgMzk1LjhjNDAuMy0xNSA2MS40LTQzLjggNjEuNC05My40UzM0OC4zIDIwOSAyOTYgMjA4LjljLTU1LjEtLjEtOTYuOCA0My42LTk2LjEgOTMuNXMyNC40IDgzIDYyLjQgOTQuOUwxOTUgNTYzQzEwNC44IDUzOS43IDEzLjIgNDMzLjMgMTMuMiAzMDIuNCAxMy4yIDE0Ny4zIDEzNy44IDIxLjUgMjk0IDIxLjVzMjgyLjggMTI1LjcgMjgyLjggMjgwLjhjMCAxMzMtOTAuOCAyMzcuOS0xODIuOSAyNjEuMWwtNjUuMi0xNjcuNnoiIGZpbGw9IiNmZmYiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxOS4yMTIiIHN0cm9rZS1saW5lam9pbj0icm91bmQiLz48L3N2Zz4%3D)](https://github.com/bkahlert/netmon/blob/main/LICENSE) [![Buy Me A Coffee](https://img.shields.io/static/v1?label=&message=%E2%98%95%20Buy%20Me%20A%20Coffee&color=FFDD00)](https://www.buymeacoffee.com/bkahlert)

## About

**Netmon** is a network monitor that detects and displays changes in your home network.

It uses `nmap` to detect devices and additionally used mDNS and NetBIOS to resolve hostnames.

[![screenshot of the loading showing a Kaomoji wizard](./docs/netmon-loading.gif)
Loading screen](./docs/netmon-loading.gif)

[![screenshot of Netmon showing a recent network scan with 13 online and 2 offline hosts](docs/netmon-running.gif)
Recent network scan](./docs/netmon-running.gif)

The application consists of three independent parts:

- a JVM-based network scanner that publishes appearing and disappearing hosts using MQTT,
- a Kotlin/JS and [Fritz2](https://github.com/jwstegemann/fritz2) based web interface that display the results, by subscribing to MQTT, and
- two Debian packages, `netmon-scanner` and `netmon-display`, from a signed apt repository at
  [bkahlert.github.io/netmon](https://bkahlert.github.io/netmon/), installed on a Raspberry Pi by a
  [Pi Hero](https://github.com/bkahlert/pihero) device file.

[![photo of Netmon running on a Raspberry Pi Zero](./docs/netmon-rpi0.jpg)
Netmon on a Raspberry Pi Zero with an 7-inch screen](./docs/netmon-rpi0.jpg)

## Install on a Raspberry Pi

Netmon runs on [Pi Hero 2](https://github.com/bkahlert/pihero): copy [devices/sample/user-data](devices/sample/user-data)
and `network-config`, set the hostname, your SSH key and Wi-Fi, flash a card with pihero's `make flash`, and the board
installs `netmon-scanner` (the scanner, Mosquitto with a websocket listener) and `netmon-display` (the web display behind
lighttpd, shown full screen by `pihero-kiosk`). [devices/README.md](devices/README.md) has the details, including the one
line a panel without EDID needs. The scanner reads `/etc/netmon/scanner.conf` (`BROKER_HOST`, `BROKER_PORT`, `NMAP_*`,
`JAVA_TOOL_OPTIONS`); the display subscribes to the broker on the host the page was loaded from, port 8080, unless the
URL's `broker.host` and `broker.port` query parameters say otherwise. Any browser on the LAN shows the same page at
`http://<host>.local/`. Updates are `sudo apt upgrade`. Pi Hero 1's
Ansible installer is frozen at the tag
[`netmon-ansible`](https://github.com/bkahlert/netmon/tree/netmon-ansible).

## Development

### Run locally

#### Run the scanner component locally

```shell
./gradlew runJvm
```

#### Run the web display component locally

```shell
./gradlew jsBrowserDevelopmentRun --continuous
```

### Build and test the packages

```shell
# Gradle runs on JDK 17 (gradle/gradle-daemon-jvm.properties) and finds or downloads one itself
make build                                          # Gradle, then nfpm: dist/*.deb
make test                                           # tier 0 (static checks, unit tests) and tier 1 (install into a systemd container)
make test-tier2                                     # tier 2: boot a QEMU VM from devices/sample, scan, show the page in WebKit and the kiosk
make deploy TARGET=pi@netmon.local                  # the built packages onto a device, no repository involved
make soak TARGET=pi@netmon.local                    # ten minutes of memory samples of both units: dist/ssh/soak.md (the VM without TARGET)
make apt-probe TARGET=pi@netmon.local               # apt update and a reinstall next to the live stack, timed, with apt's peak
make device-model-codes                             # the model codes and SF Symbols the display draws, from this Mac
```

The harness is [pihero-testkit](https://github.com/bkahlert/pihero/tree/main/testkit); `uv run pytest -m installed
--target=ssh --target-uri=pi@netmon.local` checks a running device against the tests. A release is `make release
VERSION=X.Y.Z` and `git push origin vX.Y.Z`; the workflow builds, signs and publishes the repository.

The model codes the scanner recognises, and the description and SF Symbol the display draws for each, are
`src/commonMain/resources/assets/device-model-codes.json`. `make device-model-codes` regenerates it on a Mac with
[device-icons](https://github.com/bkahlert/device-icons), which reads them from macOS itself; the codes of the Fire TV
and Sonos devices the enrichers report, and the symbols of Apple types that declare none, are
[tests/device_model_codes.py](tests/device_model_codes.py)'s own.

Tier 2 needs QEMU (`brew install qemu`) and Playwright's WebKit (`make browser`, downloaded once into Playwright's cache). It
boots the real Raspberry Pi OS root filesystem with [devices/sample/user-data](devices/sample/user-data), rendered for the VM
by `tests/vm_device.py`, lets the scanner scan QEMU's network, loads the page in WebKit at the panel's 800×480, and lets
`pihero-kiosk` show it on the VM's virtual display of that size; the run leaves `dist/tier2/display.png`, the page as WebKit
renders it, and `dist/tier2/kiosk.png`, the same page as cog paints it in the VM. `make vm` keeps the VM running for a look
around, and `make display URL=…` opens any page, the VM's, the board's or a dev server's, in that WebKit at that size.
`make release` builds, then runs tiers 0 to 2. Tier 2 also runs weekly in CI under software emulation
([weekly.yml](.github/workflows/weekly.yml)), the signal for what rotted between releases.

### MQTT

#### Publish a host event

```shell
BROKER_HOST=test.mosquitto.org BROKER_PORT=1883
mqtt pub -t "dt/netmon/test/en0/10.10.10.0/24/host" -m '{
    "event": "host",
    "type": "up",
    "host": {
      "ip": "10.10.10.10",
      "name": "test.local",
      "status": "up",
      "since": 1692455344
    }
}' -r -h "$BROKER_HOST" -p "$BROKER_PORT"
```

#### Subscribe to host events

```shell
BROKER_HOST=test.mosquitto.org BROKER_PORT=1883
mqtt sub -t dt/netmon/+/+/+/+/host -h "$BROKER_HOST" -p "$BROKER_PORT" -J
```

#### Delete a retained host event

```shell
BROKER_HOST=test.mosquitto.org BROKER_PORT=1883
mqtt pub -t "dt/netmon/test/en0/10.10.10.0/24/host" -m '' -r -h "$BROKER_HOST" -p "$BROKER_PORT"
```

### Update MQTT.js

```shell
(cd mqtt.js && ./build.sh)
```

See [mqtt.js/README.md](mqtt.js/README.md) for details.

## Contributing

Want to contribute? Awesome! The most basic way to show your support is to star the project, or to raise issues. You
can also support this project by making
a [PayPal donation](https://www.paypal.me/bkahlert) to ensure this journey continues indefinitely!

Thanks again for your support, it is much appreciated! :pray:

## License

MIT. See [LICENSE](LICENSE) for more details.
