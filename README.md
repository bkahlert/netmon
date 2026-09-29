# Netmon [![License](https://img.shields.io/github/license/bkahlert/netmon?color=29ABE2&label=License&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA1OTAgNTkwIiAgeG1sbnM6dj0iaHR0cHM6Ly92ZWN0YS5pby9uYW5vIj48cGF0aCBkPSJNMzI4LjcgMzk1LjhjNDAuMy0xNSA2MS40LTQzLjggNjEuNC05My40UzM0OC4zIDIwOSAyOTYgMjA4LjljLTU1LjEtLjEtOTYuOCA0My42LTk2LjEgOTMuNXMyNC40IDgzIDYyLjQgOTQuOUwxOTUgNTYzQzEwNC44IDUzOS43IDEzLjIgNDMzLjMgMTMuMiAzMDIuNCAxMy4yIDE0Ny4zIDEzNy44IDIxLjUgMjk0IDIxLjVzMjgyLjggMTI1LjcgMjgyLjggMjgwLjhjMCAxMzMtOTAuOCAyMzcuOS0xODIuOSAyNjEuMWwtNjUuMi0xNjcuNnoiIGZpbGw9IiNmZmYiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxOS4yMTIiIHN0cm9rZS1saW5lam9pbj0icm91bmQiLz48L3N2Zz4%3D)](https://github.com/bkahlert/netmon/blob/master/LICENSE) [![Buy Me A Coffee](https://img.shields.io/static/v1?label=&message=%E2%98%95%20Buy%20Me%20A%20Coffee&color=FFDD00)](https://www.buymeacoffee.com/bkahlert)

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
`JAVA_TOOL_OPTIONS`), the display takes its broker from the kiosk URL's `broker.host` and `broker.port` query parameters.
Any browser on the LAN shows the same page at `http://<host>.local/?broker.host=<host>.local&broker.port=8080`; opened
without the parameters the page falls back to its built-in default broker. Updates are `sudo apt upgrade`. Pi Hero 1's
Ansible installer is frozen at the tag
[`netmon-ansible`](https://github.com/bkahlert/netmon/tree/netmon-ansible).

## Development

### Run locally

#### Run the scanner component locally

```shell
./gradlew runShadow
```

#### Run the web display component locally

```shell
./gradlew jsBrowserDevelopmentRun --continuous
```

### Build and test the packages

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # Gradle 8.2 runs on JDK 17
make build                                          # Gradle, then nfpm: dist/*.deb
make test                                           # tier 0 (static checks, unit tests) and tier 1 (install into a systemd container)
make deploy TARGET=pi@netmon.local                  # the built packages onto a device, no repository involved
```

The harness is [pihero-testkit](https://github.com/bkahlert/pihero/tree/main/testkit); `uv run pytest -m installed
--target=ssh --target-uri=pi@netmon.local` checks a running device against the tests. A release is `make release
VERSION=X.Y.Z` and `git push origin vX.Y.Z`; the workflow builds, signs and publishes the repository.

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
