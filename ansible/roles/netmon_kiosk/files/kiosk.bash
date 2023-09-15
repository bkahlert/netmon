#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}" || true)")" >/dev/null 2>&1 && pwd)"

# shellcheck source=./../../netmon/files/lib/lib.bash
. "$SCRIPT_DIR/lib/lib.bash"

+diag() {
    declare kiosk_services=(display-manager)

    check_start "Kiosk diagnostics"

    for service in "${kiosk_services[@]}"; do
        check_unit "$service"
        check "service is running" systemctl -q is-active "$service.service"
        case "$service" in
        display-manager)
            if ! check "X log contains no errors" ! grep -q '] (EE)' /var/log/Xorg.0.log; then
                local error_lines=()
                readarray -t error_lines < <(grep '] (EE)' /var/log/Xorg.0.log || true)
                check_raw '%s\n' '```' 'ERRORS:' "${error_lines[@]}" '```'
            fi
            check "lightdm is configured to use user-session netmon-kiosk" grep -q '^user-session=netmon-kiosk$' /etc/lightdm/lightdm.conf
            check "netmon-kiosk.desktop is installed" [ -f /usr/share/xsessions/netmon-kiosk.desktop ]
            check "netmon-kiosk executable is installed" [ -f /opt/netmon/bin/netmon-kiosk ]
            check "NETMON_KIOSK_URL is set in /etc/environment" grep -q '^NETMON_KIOSK_URL=' /etc/environment
            check "NETMON_KIOSK_URL is not empty" [ -n "$NETMON_KIOSK_URL" ]
            ;;
        esac
    done

    # shellcheck disable=SC2016
    {
        check_further '- for tips and tricks, see [%s](%s)' 'raspberrypi.com' 'https://www.raspberrypi.com/documentation/computers/config_txt.html#which-values-are-valid-for-my-monitor'
        for service in "${kiosk_services[@]}"; do
            check_further_unit '%s service' "$service"
            check_further '- check status:\n  `%s`' "systemctl status $service.service"
            check_further '- check logs:\n  `%s`' "journalctl -b -e -u $service.service"
            check_further '- stop service:\n  `%s`' "sudo systemctl stop $service.service"
            check_further '- start service interactively:\n  `%s`' "$(service_start_cmdline "$service.service")"
            case "$service" in
            display-manager)
                check_further '- check .xsession-errors:\n  `%s`' "cat ~/.xsession-errors"
                check_further '- check lightdm configuration:\n  `%s`' "lightdm --show-config"
                check_further '- check lightdm logs:\n  `%s`' "sudo cat /var/log/lightdm/lightdm.log"
                check_further '- check lightdm x logs:\n  `%s`' "sudo cat /var/log/lightdm/x-0.log"
                check_further '- check Xorg logs:\n  `%s`' "cat /var/log/Xorg.0.log"
                check_further '- check Xorg errors:\n  `%s`' "grep -e \(EE\) /var/log/Xorg.0.log"
                check_further '- check kiosk logs:\n  `%s`' 'cat "$HOME/.kiosk.log"'
                check_further '```\n%s\n```' "$(<"$HOME/.kiosk.log")"
                ;;
            esac
        done
    }
    check_summary
}

+refresh() {
    export DISPLAY=:0
    local wid
    wid=$(xdotool search --sync --onlyvisible --class chromium | head -1) || die "failed to find Chromium window"
    xdotool windowactivate "$wid" || die "%s: failed to activate Chromium window" "$wid"
    xdotool key ctrl+F5 || die "%s: failed to send Ctrl+F5 to Chromium window" "$wid"
}
