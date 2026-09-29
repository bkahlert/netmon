# shellcheck shell=sh
lighty-enable-mod netmon >/dev/null 2>&1 || true
if [ -d /run/systemd/system ]; then
  deb-systemd-invoke try-restart lighttpd.service >/dev/null || true
fi
