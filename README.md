# Netmon

A nmap based network monitor that publishes appearing and disappearing hosts using MQTT.
Raspberry PI application to monitor your network.

## Installation

```shell
sudo apt-get install -y nmap
```

## Development

Based on a great article by [zone84][kotlin-native-raspberry-1] I was able to get a Kotlin/Native application running on my Raspberry PI.

```shell
./gradlew linkReleaseExecutableNative
scp build/bin/native/releaseExecutable/netmon.kexe bkahlert@unicorn-hd.local:/home/bkahlert/
ssh bkahlert@unicorn-hd.local './netmon.kexe'
```

```shell
# cinterop

# find file with name libbcm2835.a recursively in /
find / -name libbcm2835.a 2>/dev/null
```

[kotlin-native-raspberry-1]: https://zone84.tech/programming/kotlin-native-and-raspberry-pi-pt-1-build-script/
