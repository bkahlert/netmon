#!/usr/bin/env bash
#
# Builds all the netmon artifacts to NETMON_DATA_DIR
#
SELF_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)
NETMON_PROJECT_DIR="$SELF_DIR/.."
NETMON_DATA_DIR="$SELF_DIR/rootfs"
NETMON_APP_DIR=/home/pi/netmon

[ -d "$NETMON_PROJECT_DIR" ] || { printf "\033[31mERROR: \033[3m%s\033[23m not found.\033[0m\n" "$NETMON_PROJECT_DIR" >&2 && exit 1; }
[ -d "$NETMON_DATA_DIR" ] || { printf "\033[31mERROR: \033[3m%s\033[23m not found.\033[0m\n" "$NETMON_DATA_DIR" >&2 && exit 1; }

mkdir -p "$NETMON_DATA_DIR/$NETMON_APP_DIR"

(
    cd "$NETMON_PROJECT_DIR" || exit 1
    ./gradlew --no-daemon clean shadowJar jsBrowserProductionWebpack
    rsync -rvz --delete \
        build/libs/netmon-all.jar \
        "$NETMON_DATA_DIR/$NETMON_APP_DIR/netmon-scanner.jar"
    rsync -rvz --delete \
        build/dist/js/productionExecutable/ \
        "$NETMON_DATA_DIR/$NETMON_APP_DIR/netmon-web-display/"
)
