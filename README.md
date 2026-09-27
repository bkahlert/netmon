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
- a [Ansible-based installer](ansible/README.md) that installs everyone on a Raspberry Pi (including the Pi 1 and Zero).

[![photo of Netmon running on a Raspberry Pi Zero](./docs/netmon-rpi0.jpg)
Netmon on a Raspberry Pi Zero with an 7-inch screen](./docs/netmon-rpi0.jpg)

Find detailed installation instructions in [ansible/README.md](ansible/README.md).

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

### Run remotely

If you [installed Netmon on a Raspberry Pi](ansible/README.md), you can use
the handy [patch tool](ansible/patch).

Just switch the directory, make [patch](ansible/patch) executable, and
set the `HOST` to work with:

```shell
cd ansible
chmod +x patch
export HOST=foo.local
```

#### Build and update the remote scanner component

```shell
SCANNER=1 ./patch
```

#### Build and update the remote web display component

```shell
WEB_DISPLAY=1 ./patch
```

#### Build and update the remote scanner *and* web display component

```shell
SCANNER=1 WEB_DISPLAY=1 ./patch
```

> 💡 You can export your preferred settings, e.g. `export SCANNER=1 WEB_DISPLAY=1` to only have to type `./patch`.

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
