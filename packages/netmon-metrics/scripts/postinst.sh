# shellcheck shell=sh
if ! getent passwd netmon-metrics >/dev/null; then
  adduser --quiet --system --group --no-create-home --home /nonexistent --shell /usr/sbin/nologin netmon-metrics
fi
