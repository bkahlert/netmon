#!/usr/bin/env bash
#
# Patches a host running PiSignate to run netmon.
#
SELF_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)
NETMON_DATA_DIR="$SELF_DIR/rootfs"
NETMON_APP_DIR=/home/pi/netmon
NETMON_HOST=${NETMON_HOST:-netmon.local}
NETMON_USER=pi
PISIGNAGE_MEDIA_DIR=/home/pi/media
PISIGNAGE_START=/home/pi/start.sh
SYSTEMD_SYSTEM_DIR=/etc/systemd/system
MOTD=/etc/motd

[ -d "$NETMON_DATA_DIR" ] || { printf "\033[31mERROR: \033[3m%s\033[23m not found.\033[0m\n" "$NETMON_DATA_DIR" >&2 && exit 1; }

printf "Checking connection to \033[3m%s\033[23m... " "$NETMON_HOST" >&2
ssh -q \
    -o ConnectTimeout=5 \
    -o UserKnownHostsFile=/dev/null \
    -o StrictHostKeyChecking=no \
    "$NETMON_USER"@"$NETMON_HOST" 'exit 0' || {
    printf "\033[31mERROR: Failed to connect using \033[3mssh %s@%s\033[23m\033[0m\n" "$NETMON_USER" "$NETMON_HOST" >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2

printf "Installing dependencies... \n" >&2
ssh "$NETMON_USER"@"$NETMON_HOST" '
PKGS=(nmap openjdk-8-jre)
PKGS_COMPLETE=true
for pkg in "${PKGS[@]}"; do
    if ! dpkg-query -W -f='\''${Status}'\'' "$pkg" 2>/dev/null | grep -q "ok installed"; then
        PKGS_COMPLETE=false
    fi
done

if ! "$PKGS_COMPLETE"; then
    sudo apt-get update --allow-releaseinfo-change
    sudo apt-get install -y nmap openjdk-8-jre
fi

for npm_module in http-server live-server; do
    command -v "$npm_module" >/dev/null 2>&1 || sudo npm install -g "$npm_module"
done
'

printf "Copying \033[3m%s\033[23m to \033[3m%s\033[23m... " "$NETMON_DATA_DIR/" "$NETMON_USER"@"$NETMON_HOST":/tmp/netmon/ >&2
rsync -rz --delete --rsync-path='sudo rsync' \
    "$NETMON_DATA_DIR/" \
    "$NETMON_USER"@"$NETMON_HOST":/tmp/netmon/ || {
    printf "\033[31mERROR: Failed to copy data \033[3m%s\033[23m to \033[3m%s\033[23m\n" "$NETMON_USER" "$NETMON_USER"@"$NETMON_HOST":/tmp/netmon/ >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2

#
printf "Moving \033[3m%s\033[23m into place... " "/tmp/netmon$SYSTEMD_SYSTEM_DIR/" >&2
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "sudo chown -R root:root '/tmp/netmon$SYSTEMD_SYSTEM_DIR' && sudo chmod -R 0755 '/tmp/netmon$SYSTEMD_SYSTEM_DIR'" || {
    printf "\033[31mERROR: Failed to set permissions of \033[3m%s\033[23m" "/tmp/netmon$SYSTEMD_SYSTEM_DIR" >&2
    exit 1
}
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "sudo rsync -az '/tmp/netmon$SYSTEMD_SYSTEM_DIR/' '$SYSTEMD_SYSTEM_DIR/'" || {
    printf "\033[31mERROR: Failed to copy data \033[3m%s\033[23m to \033[3m%s\033[23m\n" "/tmp/netmon$SYSTEMD_SYSTEM_DIR/" "$SYSTEMD_SYSTEM_DIR/" >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2

#
printf "Moving \033[3m%s\033[23m into place... " "/tmp/netmon$NETMON_APP_DIR/" >&2
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "rsync -rz --delete '/tmp/netmon$NETMON_APP_DIR/' '$NETMON_APP_DIR/'" || {
    printf "\033[31mERROR: Failed to copy data \033[3m%s\033[23m to \033[3m%s\033[23m\n" "/tmp/netmon$NETMON_APP_DIR/" "$NETMON_APP_DIR/" >&2
    exit 1
}
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "chmod -R 0744 '$NETMON_APP_DIR' && chmod -R +x '$NETMON_APP_DIR'/*.sh" || {
    printf "\033[31mERROR: Failed to set permissions of \033[3m%s\033[23m" "$NETMON_APP_DIR" >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2

#
printf "Moving \033[3m%s\033[23m into place... " "/tmp/netmon$PISIGNAGE_MEDIA_DIR/" >&2
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "rsync -rz '/tmp/netmon$PISIGNAGE_MEDIA_DIR/' '$PISIGNAGE_MEDIA_DIR/'" || {
    printf "\033[31mERROR: Failed to copy data \033[3m%s\033[23m to \033[3m%s\033[23m\n" "/tmp/netmon$PISIGNAGE_MEDIA_DIR/" "$PISIGNAGE_MEDIA_DIR/" >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2

#
printf "Updating services... \n" >&2
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" '
NETMON_SERVICES=(
  netmon-scanner.service
  netmon-web-display.service
)

sudo systemctl daemon-reload

for service in "${NETMON_SERVICES[@]}"; do
  sudo systemctl -q disable "$service"
done

for service in "${NETMON_SERVICES[@]}"; do
  sudo systemctl restart "$service"
done
' || {
    printf "\033[31mERROR: Failed to update services" >&2
    exit 1
}

#
printf "Moving modified \033[3m%s\033[23m into place... " "/tmp/netmon$PISIGNAGE_START" >&2
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "sudo chown root:root '/tmp/netmon$PISIGNAGE_START' && sudo chmod -R 0777 '/tmp/netmon$PISIGNAGE_START'" || {
    printf "\033[31mERROR: Failed to set permissions of \033[3m%s\033[23m" "/tmp/netmon$PISIGNAGE_START" >&2
    exit 1
}
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "sudo rsync -az '/tmp/netmon$PISIGNAGE_START' '$PISIGNAGE_START'" || {
    printf "\033[31mERROR: Failed to copy data \033[3m%s\033[23m to \033[3m%s\033[23m\n" "/tmp/netmon$PISIGNAGE_START" "$PISIGNAGE_START" >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2

#
printf "Moving \033[3m%s\033[23m into place... " "/tmp/netmon$MOTD" >&2
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "sudo chown root:root '/tmp/netmon$MOTD' && sudo chmod -R 0644 '/tmp/netmon$MOTD'" || {
    printf "\033[31mERROR: Failed to set permissions of \033[3m%s\033[23m" "/tmp/netmon$MOTD" >&2
    exit 1
}
# shellcheck disable=SC2029
ssh "$NETMON_USER"@"$NETMON_HOST" "sudo rsync -az '/tmp/netmon$MOTD' '$MOTD'" || {
    printf "\033[31mERROR: Failed to copy data \033[3m%s\033[23m to \033[3m%s\033[23m\n" "/tmp/netmon$MOTD" "$MOTD" >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2

#
printf "Restarting UI... " >&2
curl -s "http://$NETMON_HOST:8000/api/play/playlists/netmon-web-display" -u "$NETMON_USER":pi \
    -H 'Content-Type: application/json;charset=UTF-8' \
    --data-raw '{"play":true}' >/dev/null || {
    printf "\033[31mERROR: Failed to restart UI\033[23m\n" >&2
    exit 1
}
printf "\033[32m✔︎\033[0m\n" >&2
