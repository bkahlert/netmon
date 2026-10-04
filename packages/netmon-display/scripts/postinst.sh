# shellcheck shell=sh
# Up to 2.2 this package ran netmon-display-stats.service; dpkg removes its file on upgrade but not the running
# process or its enablement. netmon-metrics publishes the figures now.
if [ -d /run/systemd/system ]; then
  deb-systemd-invoke stop netmon-display-stats.service >/dev/null || true
fi
deb-systemd-helper purge netmon-display-stats.service >/dev/null || true
lighty-enable-mod netmon >/dev/null 2>&1 || true
if [ -d /run/systemd/system ]; then
  deb-systemd-invoke try-restart lighttpd.service >/dev/null || true
  deb-systemd-invoke try-restart pihero-kiosk.service >/dev/null || true
fi
