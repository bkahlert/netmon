#!/bin/bash

# PiSignage has a weird startup process:
# - /etc/xdg/lxsession/LXDE-pi/autostart
#   - starts /home/pi/start.sh
#     - starts `/home/pi/piSignagePro/pi-monitor.js`
#       - starts `/home/pi/piSignagePro/pi-server.js`
#
# An invocation of this script is added to /home/pi/start.sh

NETMON_SERVICES=(
  netmon-scanner.service
  netmon-web-display.service
)

while true; do
  completed=1
  for service in "${NETMON_SERVICES[@]}"; do
    if ! systemctl -q is-active "$service"; then
      printf "%s is not running yet...\n" "$service"
      completed=0
    fi
  done
  if [ "$completed" -ne 1 ]; then
    printf "Checking again in 5 seconds...\n"
    sleep 5
  fi
done

printf "All netmon services (%s) are running. Continuing...\n" "${NETMON_SERVICES[*]}"
