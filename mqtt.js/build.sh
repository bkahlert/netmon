#!/usr/bin/env bash

# see https://github.com/mqttjs/MQTT.js/tree/main#browserify, *and*
# https://github.com/mqttjs/MQTT.js/tree/main#webpack

command -v webpack >/dev/null 2>&1 || npm install -g webpack

MQTT_BROWSER_BUILD_DIR=build
RESOURCES_DIR=../src/jsMain/resources

(
    [ ! -d "$MQTT_BROWSER_BUILD_DIR" ] || rm -rf "$MQTT_BROWSER_BUILD_DIR"
    [ -d "$MQTT_BROWSER_BUILD_DIR" ] || mkdir -p "$MQTT_BROWSER_BUILD_DIR"

    cd "$MQTT_BROWSER_BUILD_DIR" || { printf "\e[31mERROR: Failed to cd to \e[3m%s\e[23m.\e[0m\n" "$MQTT_BROWSER_BUILD_DIR" >&2 && exit 1; }

    npm install mqtt
    npm install tinyify

    cd node_modules/mqtt || { printf "\e[31mERROR: Failed to cd to \e[3m%s\e[23m.\e[0m\n" node_modules/mqtt >&2 && exit 1; }

    # As of 2023-07-25, the following manual build does
    # neither seem to work (for example, missing tsconfig.build.json)
    # nor does it seem to be necessary, because the included dist
    # directory ships a working release.

    # npm install .
    # webpack mqtt.js ./browserMqtt.js --output-library mqtt

    cp dist/mqtt*.js ../../..
    printf "\n"
)

cd "$RESOURCES_DIR" || { printf "\e[31mERROR: Failed to cd to \e[3m%s\e[23m.\e[0m\n" "$RESOURCES_DIR" >&2 && exit 1; }

printf "Creating symbolic links in \e[3m%s\e[23m:\n" "$RESOURCES_DIR"
for f in ../../../mqtt.js/mqtt.js; do
    printf -- "  - \e[3m%s\e[23m → \e[3m%s\e[23m... " "${f##*/}" "$f"
    ln -sf "$f" "${f##*/}"
    printf "\e[32;1m✔\e[0m\n"
done
printf "\n"

printf "Run \e[3m%s\e[23m to verify if the build is working.\n" "./test.sh"
