# Netmon

A nmap based network scanner that

- publishes appearing and disappearing hosts using MQTT, and
- provides a Kotlin/JS + Fritz2 based web interface to display the results.

## Installation

```shell
sudo apt-get install -y nmap
```

## Update MQTT.js

```shell
(cd mqtt.js && ./build.sh)
```

See [mqtt.js/README.md](mqtt.js/README.md) for details.

## Development

### Network scanner

```shell
# Compile and run
./gradlew runShadow

# Compile and push to Raspberry Pi
./gradlew --no-daemon clean shadowJar \
  && rsync -rvz --delete \
  build/libs/netmon-all.jar \
  pi@192.168.16.46:/home/pi/netmon/netmon-scanner.jar
```

### Web Display

```shell
# Compile and run
./gradlew jsBrowserDevelopmentRun --continuous

# Compile development distribution and push to Raspberry Pi
./gradlew --no-daemon clean jsBrowserDevelopmentExecutableDistribution \
  && rsync -rvz --delete \
  build/dist/js/developmentExecutable/ \
  pi@192.168.16.46:/home/pi/netmon/netmon-web-display/
  
# Compile production distribution and push to Raspberry Pi
./gradlew --no-daemon clean jsBrowserProductionWebpack \
  && rsync -rvz --delete \
  build/dist/js/productionExecutable/ \
  pi@192.168.16.46:/home/pi/netmon/netmon-web-display/
```

### Copy SSH key

```shell
sshpass -p raspberry \
  ssh-copy-id \
      -i ~/.ssh/id_rsa20 \
      -o UserKnownHostsFile=/dev/null \
      -o StrictHostKeyChecking=no \
      -o PreferredAuthentications=password \
      pi@10.0.0.2
```

```shell
./gradlew runShadowSsh -Pssh.destination='pi@10.0.0.2'
```

```shell
# cinterop

# find file with name libbcm2835.a recursively in /
find / -name libbcm2835.a 2>/dev/null
```

[kotlin-native-raspberry-1]: https://zone84.tech/programming/kotlin-native-and-raspberry-pi-pt-1-build-script/
