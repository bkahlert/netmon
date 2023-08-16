# Shared functions

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

# shellcheck source=./services.bash
source "$SCRIPT_DIR/services.bash"

# shellcheck source=./checks.bash
source "$SCRIPT_DIR/checks.bash"
