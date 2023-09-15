#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}" || true)")" >/dev/null 2>&1 && pwd)"

# shellcheck source=./../../netmon/files/lib/lib.bash
. "$SCRIPT_DIR/lib/lib.bash"

declare scanner_services=(netmon-scanner)

+diag() {
    check_start "Scanner diagnostics"

    for service in "${scanner_services[@]}"; do
        check_unit "$service"
        case "$service" in
        netmon-scanner)
            check "Java is installed" sh -c 'command -v java >/dev/null 2>&1'
            check "Java is operational" sh -c 'java -version >/dev/null 2>&1'
            ;;
        esac
        check "service is running" systemctl -q is-active "$service.service"
    done

    # shellcheck disable=SC2016
    {
        for service in "${scanner_services[@]}"; do
            check_further_unit '%s service' "$service"
            check_further '- check status:\n  `%s`' "systemctl status $service.service"
            check_further '- check logs:\n  `%s`' "journalctl -b -e -u $service.service"
            check_further '- stop service:\n  `%s`' "sudo systemctl stop $service.service"
            check_further '- start service interactively:\n  `%s`' "$(service_start_cmdline "$service.service")"
        done
    }
    check_summary
}

+start() {
    for service in "${scanner_services[@]}"; do
        sudo systemctl start "$service.service"
    done
}

+restart() {
    sudo systemctl daemon-reload
    for service in "${scanner_services[@]}"; do
        sudo systemctl restart "$service.service"
    done
}

+stop() {
    for service in "${scanner_services[@]}"; do
        sudo systemctl stop "$service.service"
    done
}

+debug() {
    +stop
    local cmdline=(
        java
        -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
        -Xdebug
        -Dbroker.host="localhost"
        -Dbroker.port="8080"
        -jar "/opt/netmon/netmon-scanner/"*.jar
        "$@"
    )
    printf "Starting the scanner in debug mode using the following command line (including the provided arguments):\n%s\n" "${cmdline[*]}"
    sudo "${cmdline[@]}"
}
