# shellcheck shell=sh
rm -rf /var/lib/netmon /var/cache/netmon
if getent passwd netmon >/dev/null; then
  deluser --quiet --system netmon >/dev/null 2>&1 || true
fi
if [ -d /run/systemd/system ]; then
  deb-systemd-invoke try-restart mosquitto.service >/dev/null || true
fi
