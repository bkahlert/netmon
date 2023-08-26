#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}" || true)")" >/dev/null 2>&1 && pwd)"

# shellcheck source=./../../netmon/files/lib/lib.bash
. "$SCRIPT_DIR/lib/lib.bash"

declare web_display_services=(lighttpd)

+diag() {
    check_start "Web display diagnostics"

    for service in "${web_display_services[@]}"; do
        check_unit "$service"
        check "service is running" systemctl -q is-active "$service.service"
        case "$service" in
        lighttpd)
            check "lighttpd is configured to use /opt/netmon/netmon-web-display as document root" grep -q '^server.document-root\s\+=\s\+"/opt/netmon/netmon-web-display"$' /etc/lighttpd/lighttpd.conf
            ;;
        esac
    done

    # shellcheck disable=SC2016
    {
        for service in "${web_display_services[@]}"; do
            check_further_unit '%s service' "$service"
            check_further '- check status:\n  `%s`' "systemctl status $service.service"
            check_further '- check logs:\n  `%s`' "journalctl -b -e -u $service.service"
            check_further '- stop service:\n  `%s`' "sudo systemctl stop $service.service"
            check_further '- start service interactively:\n  `%s`' "$(service_start_cmdline "$service.service")"
        done
    }
    check_summary
}

sctl() {
    local action="${1?}"
    shift
    for service in "${web_display_services[@]}"; do
        sudo systemctl "$action" "$service.service"
    done
}

+start() {
    sctl start
}

+restart() {
    sctl restart
}

+stop() {
    sctl stop
}
