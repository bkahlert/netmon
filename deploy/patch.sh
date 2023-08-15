#!/usr/bin/env bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
NETMON_PROJECT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

NETMON_HOST=${NETMON_HOST:-foo.local}
NETMON_KIOSK_EXECUTABLE="$SCRIPT_DIR/roles/netmon_kiosk/files/netmon-kiosk"

m() {
    ansible "$NETMON_HOST" --become -m "${1?module missing}" -a "${2?arguments missing}"
}

gradle_args=()
gradle_build=0
update_scanner=0
update_web_display=0
while (($#)); do
    case "$1" in
    --clean)
        gradle_args+=(--no-daemon clean)
        ;;
    --scanner)
        gradle_args+=(shadowJar)
        gradle_build=1
        update_scanner=1
        ;;
    --web-display)
        gradle_args+=(jsBrowserProductionWebpack)
        gradle_build=1
        update_web_display=1
        ;;
    *)
        echo "Unknown argument: $1"
        exit 1
        ;;
    esac
    shift
done

if [ "$gradle_build" = 1 ]; then
    (
        set -x
        cd "$NETMON_PROJECT_DIR" || exit 1
        ./gradlew "${gradle_args[@]}"
    )
fi

if [ "$update_scanner" = 1 ]; then
    m ansible.builtin.service "name=netmon-scanner state=stopped"
    m ansible.builtin.copy "src='$NETMON_PROJECT_DIR/build/libs/' dest=/opt/netmon/netmon-scanner/"
    m ansible.builtin.service "name=netmon-scanner state=restarted"
fi

if [ "$update_web_display" = 1 ]; then
    m ansible.builtin.service "name=netmon-web-display state=stopped"
    m ansible.builtin.copy "src='$NETMON_PROJECT_DIR/build/dist/js/productionExecutable/' dest=/opt/netmon/netmon-web-display/"
    m ansible.builtin.service "name=netmon-web-display state=restarted"
fi

m ansible.builtin.copy "src='$NETMON_KIOSK_EXECUTABLE' dest=/opt/netmon/bin/netmon-kiosk"
m ansible.builtin.service "name=lightdm state=restarted"
