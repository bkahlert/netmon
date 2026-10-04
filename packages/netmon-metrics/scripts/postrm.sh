# shellcheck shell=sh
if getent passwd netmon-metrics >/dev/null; then
  deluser --quiet --system netmon-metrics >/dev/null 2>&1 || true
fi
