# shellcheck shell=sh
if ! getent passwd netmon >/dev/null; then
  adduser --quiet --system --group --no-create-home --home /var/lib/netmon --shell /usr/sbin/nologin netmon
fi
# The websocket listener is a new file under Mosquitto's conf.d; the running broker reads it on restart.
if [ -d /run/systemd/system ]; then
  deb-systemd-invoke try-restart mosquitto.service >/dev/null || true
fi
