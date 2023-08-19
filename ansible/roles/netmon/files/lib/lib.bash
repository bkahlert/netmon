# Shared functions

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
SCRIPT_BASENAME="$(basename "${BASH_SOURCE[0]}")"

for lib in "$SCRIPT_DIR"/*.bash; do
    # shellcheck source=./runs.bash
    # shellcheck source=./services.bash
    # shellcheck source=./checks.bash
    [ "$lib" = "$SCRIPT_DIR/$SCRIPT_BASENAME" ] || source "$lib"
done
