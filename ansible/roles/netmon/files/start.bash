#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}" || true)")" >/dev/null 2>&1 && pwd)"

# shellcheck source=./../../netmon/files/lib/lib.bash
. "$SCRIPT_DIR/lib/lib.bash"

+main() {
    trap 'tput cnorm || true' EXIT
    tput civis || true

    local extension_dirs=("$SCRIPT_DIR/..") extensions
    extensions=$(run_caching find_extensions "${extension_dirs[@]}") || die '%s: loading extensions failed' "${extension_dirs[*]}"

    local exit_code=0
    local curr_ext_file curr_ext_commands curr_ext_command curr_ext_name
    while IFS=: read -r curr_ext_file curr_ext_commands curr_ext_name; do
        [ ! "$curr_ext_name" = "start" ] || continue
        for curr_ext_command in $curr_ext_commands; do
            if [ "$curr_ext_command" = "start" ]; then
                if ! run_extension --extensions "$extensions" --extension "$curr_ext_name" -- "$curr_ext_command"; then
                    exit_code=1
                fi
                continue 2
            fi
        done
    done <<<"$extensions"

    return "$exit_code"
}
