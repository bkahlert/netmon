declare -i WIZARD_GRAY=7
declare -i WIZARD_PINK=9
declare -i WIZARD_YELLOW=11
declare -i WIZARD_BLUE=12
declare WIZARD_WAND='─'

# Prints a `gum format --type template`-processable Netmon wizard kaomoji in the given mood.
# Globals:
#   None
# Arguments:
#   --mood (string, default: neutral): The mood to use.
#   --magic (boolean, default: 1): Whether to include the magic.
#   --frame (int, default: -1): The frame index of the animation to use.
#   --offset (int, default: 0): The number of characters to drop from the left. Negative values pad the left side with spaces.
# Outputs:
#   1: The kaomoji in the given mood.
# Returns:
#   0: Always
wizard_template() {
    local mood=neutral magic=1
    local -i frame=-1 offset=0
    while [ $# -gt 0 ]; do
        case $1 in
        --mood) mood=${2?$1: parameter value not set} && shift 2 ;;
        --mood=*) mood=${1#*=} && shift ;;
        --magic) magic=${2?$1: parameter value not set} && shift 2 ;;
        --magic=*) magic=${1#*=} && shift ;;
        --frame) frame=${2?$1: parameter value not set} && shift 2 ;;
        --frame=*) frame=${1#*=} && shift ;;
        --offset) offset=${2?$1: parameter value not set} && shift 2 ;;
        --offset=*) offset=${1#*=} && shift ;;
        --) shift && break ;;
        --*) die --code 2 "%s: invalid option" "$1" ;;
        -*) die --code 2 "%s: invalid flag" "$1" ;;
        *) break ;;
        esac
    done

    local graphemes=()
    {
        # add face graphemes
        local -a face
        case $mood in
        neutral)
            face=('' 'つ' '◕' '౪' '◕' '')
            ;;
        0 | happy)
            face=('' '' '＾' '∀' '＾' '')
            ;;
        1 | 2 | sad)
            face=(' ' '' '◕' '︿' '◕' '')
            ;;
        *)
            face=('' ' ' '⊙' '﹏' '⊙' '')
            ;;
        esac
        graphemes+=(
            '{{ Foreground "'"$WIZARD_GRAY"'" "(" }}'
            ${face[0]:+'{{ Foreground "'"$WIZARD_GRAY"'" "'"${face[0]}"'" }}'} # drop if empty
            ${face[1]:+'{{ Foreground "'"$WIZARD_GRAY"'" "'"${face[1]}"'" }}'} # drop if empty
            ${face[2]:+'{{ Foreground "'"$WIZARD_GRAY"'" "'"${face[2]}"'" }}'} # drop if empty
            ${face[3]:+'{{ Foreground "'"$WIZARD_GRAY"'" "'"${face[3]}"'" }}'} # drop if empty
            ${face[4]:+'{{ Foreground "'"$WIZARD_GRAY"'" "'"${face[4]}"'" }}'} # drop if empty
            ${face[5]:+'{{ Foreground "'"$WIZARD_GRAY"'" "'"${face[5]}"'" }}'} # drop if empty
            '{{ Foreground "'"$WIZARD_GRAY"'" ")" }}'
        )

        # add hand graphemes
        graphemes+=('{{ Foreground "'"$WIZARD_WHITE"'" "つ" }}')
        # add wand graphemes
        graphemes+=('{{ Foreground "'"$WIZARD_GRAY"'" "'"$WIZARD_WAND"'" }}')

        if [ ! "$magic" = 0 ]; then
            # add magic graphemes
            local magics=(
                '｡ﾟ․☆･*ﾟ'
                '*ﾟ․｡･☆ﾟ'
                '☆ﾟ․*･｡ﾟ'
            ) magic_char
            local magic_colors=("$WIZARD_PINK" '14' "$WIZARD_YELLOW" "$WIZARD_PINK" "$WIZARD_YELLOW" '10' "$WIZARD_BLUE") magic_color=0
            while IFS='' read -r magic_char; do
                graphemes+=('{{ Foreground "'"${magic_colors[$magic_color]}"'" "'"$magic_char"'" }}')
                magic_color=$(((magic_color + 1) % ${#magic_colors[@]}))
            done < <(echo "${magics[$((frame % ${#magics[@]}))]}" | grep -o .)
        fi
    }

    # apply offset
    {
        if [ "$offset" -lt 0 ]; then
            # pad the left side with spaces
            for (( ; offset < 0; offset++)); do
                graphemes=(' ' "${graphemes[@]}")
            done
        else
            # truncate the left side
            graphemes=("${graphemes[@]:$offset}")
        fi
    }

    printf '%s' "${graphemes[@]}"
}

# Deeplink for print debugging, invocable with $0 @wizard
@wizard() {
    while [ $# -gt 0 ]; do
        case $1 in
        --help | -h)
            usage --header "Debugging utils for the Netmon wizard kaomoji" \
                --arg="${FUNCNAME[0]}" --arg=command moods frames 'animate[-record|-pre-rendered]'
            return 0
            ;;
        *) break ;;
        esac
    done

    while [ $# -gt 0 ]; do
        case $1 in
        --) shift && break ;;
        --*) die --code 2 "%s: invalid option" "$1" ;;
        -*) die --code 2 "%s: invalid flag" "$1" ;;
        *) break ;;
        esac
    done

    local command
    if [ $# -gt 0 ]; then
        command=$1 && shift
    else
        "${FUNCNAME[0]}" --help
        die --code 2 'command missing'
    fi

    case $command in
    moods)
        local cols=() col mood
        col=$(
            gum join --vertical --align right \
                '' \
                "$(gum style --faint "NO_COLOR=1" || true)" \
                '' \
                "$(gum style --faint "CLICOLOR_FORCE=1" || true)" \
                '' \
                "$(gum style --faint "TTY" || true)" \
                '' \
                "$(gum style --faint "environment    "$'\n'"            ╱ mood" || true)"
        )
        cols+=("$(gum style --padding "1 2" "$col")")
        for mood in neutral happy sad unknown; do
            local wizard
            wizard=$(wizard_template --mood "$mood" "$@" || true)
            col=$(
                gum join --vertical --align center \
                    '' \
                    "$(NO_COLOR=1 gum format --type template "$wizard" || true)" \
                    '' \
                    "$(CLICOLOR_FORCE=1 COLORTERM='' TERM=dumb format "$wizard" || true)" \
                    '' \
                    "$(COLORTERM='truecolor' format "$wizard" || true)" \
                    '' \
                    "$(gum style --faint $'\n'"$mood" || true)"
            )
            cols+=("$(gum style --padding "1 2" "$col")")
        done
        gum join --horizontal "${cols[@]}"
        ;;
    frames)
        wizard_frames "$@"
        ;;
    animate)
        wizard_animate --vertical-padding 2 --loops 1 "$@" <(wizard_frames || true)
        ;;
    animate-record)
        local recording
        recording=$(mktemp) || die --code 'Failed to create a temporary file to record the animation.'
        @wizard animate --record-to "$recording" "$@"
        if command -v pbcopy >/dev/null; then
            format <"$recording" | pbcopy
            printf '%s\n' "RECORDING: COPIED TO CLIPBOARD"
        else
            printf '%s\n' "RECORDING: START"
            format <"$recording"
            printf '\n%s\n' "RECORDING: END"
        fi >&2
        rm "$recording"
        ;;
    animate-pre-rendered)
        wizard_animate_pre_rendered "$@"
        ;;
    *)
        "${FUNCNAME[0]}" --help
        die --code 2 '%s: invalid command' "$command"
        ;;
    esac
}

# Prints all frames line by line that create the animation of
# the Netmon wizard kaomoji flying-in from the left.
# Globals:
#   CLICOLOR_FORCE: If set and not 0, the output is colored.
#   NO_COLOR: If set and not 0, the colors are suppressed.
# Arguments:
#   None
# Outputs:
#   1: Frames of the animation separated by newlines.
# Returns:
#   0: Frames successfully printed.
#   1: An error occurred.
wizard_frames() {
    local frame offset length raw_wizard
    raw_wizard=$(NO_COLOR=1 gum format --type template "$(wizard_template || true)")
    length=${#raw_wizard}
    # fly-in
    local last
    for frame in $(seq -1 "$((length))"); do
        offset=$((length - 1 - frame))
        last=$(wizard_template "$@" --frame "$frame" --offset "$offset")
        printf '%s\n' "$last"
    done
    # loop until animation repeats itself
    local curr
    while true; do
        frame=$((frame + 1))
        curr=$(wizard_template "$@" --frame "$frame" --offset "$offset")
        [ ! "$curr" = "$last" ] || break
        printf '%s\n' "$curr"
    done
}

# Renders the given frames (consumed by cat) as specified by the given arguments
# line by line.
# Globals:
#   None
# Arguments:
#   --vertical-padding (int, default: 0): Number of empty lines to add before and after the animation.
#   --ideal-frame-ms (int, default: 50): Number of milliseconds to pass between frames. If the number is too ambitious, the frames are dropped as required.
#   --loops (int, default: 0): How many times to repeat the part where's no horizontal movement, or –1 for infinite.
#   --cleanup (bool, default: 1): Whether to restore the terminal's state afterward.
#   --record-to (file, optional): Writes the screens to the given file (see @wizard for usage).
#   $@: (file ...): files containing the frames of the animation; use '-' for stdin
# Outputs:
#   1: The terminal output for the animation.
# Returns:
#   0: Frames successfully printed.
#   1: An error occurred.
wizard_animate() {
    # First, hide the cursor and make sure it's restored on exit.
    local tput_civis tput_cnorm
    tput_civis=$(tput civis)
    tput_cnorm=$(tput cnorm)
    # shellcheck disable=SC2064
    trap 'printf %s "'"$tput_cnorm"'"' EXIT
    printf %s "$tput_civis"

    local py=0
    local ideal_frame_ms=50
    local -i loops=0
    local cleanup=1
    local record_to
    while [ $# -gt 0 ]; do
        case $1 in
        --vertical-padding) py=${2?$1: parameter value not set} && shift 2 ;;
        --vertical-padding=*) py=${1#*=} && shift ;;
        --ideal-frame-ms) ideal_frame_ms=${2?$1: parameter value not set} && shift 2 ;;
        --ideal-frame-ms=*) ideal_frame_ms=${1#*=} && shift ;;
        --loops) loops=${2?$1: parameter value not set} && shift 2 ;;
        --loops=*) loops=${1#*=} && shift ;;
        --cleanup) cleanup=${2?$1: parameter value not set} && shift 2 ;;
        --cleanup=*) cleanup=${1#*=} && shift ;;
        --record-to) record_to=${2?$1: parameter value not set} && shift 2 ;;
        --record-to=*) record_to=${1#*=} && shift ;;
        --) shift && break ;;
        --*) die --code 2 "%s: invalid option" "$1" ;;
        -*) die --code 2 "%s: invalid flag" "$1" ;;
        *) break ;;
        esac
    done

    local i
    local pt=''
    local pb=''
    for ((i = 0; i < py; i++)); do
        pt="$pt"$'\n'
        pb="$pb"$'\n'
    done

    # Caching the escape sequences speeds up the animation about 10x,
    # for example, on a Raspberry Pi Zero, 0.5 s vs 5 s
    local tput_cols tput_cub_cols tput_cuu1 tput_cuu_py='' tput_el tput_sgr0
    tput_cols=$(tput cols)
    tput_cub_cols=$(tput cub "$tput_cols")
    tput_cuu1=$(tput cuu1 2>/dev/null || tput cuu 1)
    if [ "$py" -gt 0 ]; then
        for ((i = 0; i < py; i++)); do
            tput_cuu_py+="$tput_cuu1"
        done
    fi
    tput_el=$(tput el)
    tput_sgr0=$(tput sgr0)

    # setup cleanup trap
    local cleanup_sequence
    if [ ! "$cleanup" = 0 ]; then
        # Assumption: each rendered screen restores the cursor to the original position
        cleanup_sequence=''

        # Part 1: move the cursor to the last line, given the animation didn't add any lines (otherwise would have to them add here)
        # move down by the top padding
        for ((i = 0; i < py; i++)); do cleanup_sequence+=$'\n'; done
        # move down by the bottom padding
        for ((i = 0; i < py; i++)); do cleanup_sequence+=$'\n'; done

        # Part 2: clear all lines from the bottom to the top
        # jump to the start of the line and clear it (from the start)
        cleanup_sequence+="$tput_cub_cols$tput_el"
        # for each bottom padding: move up one line and clear it (from the start)
        for ((i = 0; i < py; i++)); do cleanup_sequence+="$tput_cuu1$tput_el"; done
        # for each top padding: move up one line and clear it (from the start)
        for ((i = 0; i < py; i++)); do cleanup_sequence+="$tput_cuu1$tput_el"; done

        # Part 3: reset colors and show cursor
        cleanup_sequence+="$tput_cnorm$tput_sgr0"
        trap "printf '%s' '""$cleanup_sequence""'" EXIT
    fi

    printf %s "$tput_civis" # hide cursor
    local prepared_screen last_frame_time current_time elapsed_time available_frame_time
    last_frame_time=$((${EPOCHREALTIME/./} / 1000))
    if [ -n "$record_to" ] && [ -f "$record_to" ]; then rm "$record_to"; fi
    while read -r frame; do
        current_time=$((${EPOCHREALTIME/./} / 1000))
        elapsed_time=$((current_time - last_frame_time))
        available_frame_time=$((ideal_frame_ms - elapsed_time))

        # renders the screen, which is the frame + padding + moving the cursor back to the original position
        printf -v prepared_screen %s "$pt" "$frame" "$pb" "$tput_cub_cols" "$tput_cuu_py" "$tput_cuu_py"
        [ -z "$record_to" ] || printf '%s:%q\n' "$((ideal_frame_ms / 1000)).$(printf "%03d" $((ideal_frame_ms % 1000)))" "$prepared_screen" >>"$record_to"

        # only print the screen if there's enough time left
        if [ "$available_frame_time" -gt 0 ]; then
            gum format --type template "$prepared_screen"
            prepared_screen=''
            sleep "$((available_frame_time / 1000)).$(printf "%03d" $((available_frame_time % 1000)))"
        fi

        last_frame_time=$current_time
    done < <(looped_frames --loops "$loops" "$@" || true)

    # print the last screen if was skipped due to lack of time
    if [ -n "$prepared_screen" ]; then
        gum format --type template "$prepared_screen"
    fi

    if [ ! "$cleanup" = 0 ]; then
        trap - EXIT
        printf %s "$cleanup_sequence"
        [ -z "$record_to" ] || printf '%s:%q\n' "$((ideal_frame_ms / 1000)).$(printf "%03d" $((ideal_frame_ms % 1000)))" "$cleanup_sequence" >>"$record_to"
    fi
}

looped_frames() {
    local -i loops=0
    while [ $# -gt 0 ]; do
        case $1 in
        --loops) loops=${2?$1: parameter value not set} && shift 2 ;;
        --loops=*) loops=${1#*=} && shift ;;
        --) shift && break ;;
        --*) die --code 2 "%s: invalid option" "$1" ;;
        -) break ;;
        -*) die --code 2 "%s: invalid flag" "$1" ;;
        *) break ;;
        esac
    done

    if [ "$loops" -eq 0 ]; then
        # no loops: just print the frames once
        cat "$@"
    else
        local loop_buffer && loop_buffer=$(mktemp)
        # looping: print the frames ...
        cat "$@" | tee >(
            # ... and buffer the last frames with the same line length
            local buffered_lines=() buffered_consecutive_line_length=-1 stripped_line stripped_line_length
            while IFS='' read -r line; do
                stripped_line=$(printf '%s\n' "$line" | remove_ansi_escapes)
                stripped_line_length=${#stripped_line}
                [ "$buffered_consecutive_line_length" = "$stripped_line_length" ] || buffered_lines=()
                buffered_consecutive_line_length=$stripped_line_length
                buffered_lines+=("$line")
            done
            printf '%s\n' "${buffered_lines[@]}" >"$loop_buffer"
        )
        # print the buffer until loops are reached or the buffer no longer exists
        while [ "$loops" -ne 0 ] && [ -f "$loop_buffer" ]; do
            cat "$loop_buffer" || exit # if anything goes wrong, reading from the buffer, exit
            [ "$loops" -lt 0 ] || loops=$((loops - 1))
        done
    fi
}

wizard_animate_pre_rendered() {
    tput civis
    local skip=0 screen
    for screen in "${_WIZARD_ANIMATE_PRE_RENDERED[@]}"; do
        if [ "$skip" -gt 0 ]; then
            skip=$((skip - 1))
            continue
        fi
        echo -n "${screen#*:}"
        sleep "${screen%%:*}"
        skip=0
    done
}

_WIZARD_ANIMATE_PRE_RENDERED=(
    0.050:$'\n\n\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[92m☆[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[93m･[0m[92m｡[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[91m☆[0m[93m･[0m[92m*[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[93m․[0m[91m｡[0m[93m･[0m[92m☆[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[96mﾟ[0m[93m․[0m[91m*[0m[93m･[0m[92m｡[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[91m｡[0m[96mﾟ[0m[93m․[0m[91m☆[0m[93m･[0m[92m*[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[37m─[0m[91m*[0m[96mﾟ[0m[93m․[0m[91m｡[0m[93m･[0m[92m☆[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\nつ[37m─[0m[91m☆[0m[96mﾟ[0m[93m․[0m[91m*[0m[93m･[0m[92m｡[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[37m)[0mつ[37m─[0m[91m｡[0m[96mﾟ[0m[93m․[0m[91m☆[0m[93m･[0m[92m*[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[37m◕[0m[37m)[0mつ[37m─[0m[91m*[0m[96mﾟ[0m[93m․[0m[91m｡[0m[93m･[0m[92m☆[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m☆[0m[96mﾟ[0m[93m․[0m[91m*[0m[93m･[0m[92m｡[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m｡[0m[96mﾟ[0m[93m․[0m[91m☆[0m[93m･[0m[92m*[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m*[0m[96mﾟ[0m[93m․[0m[91m｡[0m[93m･[0m[92m☆[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n[37m([0m[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m☆[0m[96mﾟ[0m[93m․[0m[91m*[0m[93m･[0m[92m｡[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n [37m([0m[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m｡[0m[96mﾟ[0m[93m․[0m[91m☆[0m[93m･[0m[92m*[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n [37m([0m[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m*[0m[96mﾟ[0m[93m․[0m[91m｡[0m[93m･[0m[92m☆[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n [37m([0m[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m☆[0m[96mﾟ[0m[93m․[0m[91m*[0m[93m･[0m[92m｡[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n [37m([0m[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m｡[0m[96mﾟ[0m[93m․[0m[91m☆[0m[93m･[0m[92m*[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n [37m([0m[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m*[0m[96mﾟ[0m[93m․[0m[91m｡[0m[93m･[0m[92m☆[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n [37m([0m[37mつ[0m[37m◕[0m[37m౪[0m[37m◕[0m[37m)[0mつ[37m─[0m[91m☆[0m[96mﾟ[0m[93m․[0m[91m*[0m[93m･[0m[92m｡[0m[94mﾟ[0m\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n\n\n\E[112D\E[K\E[A\E[K\E[A\E[K\E[A\E[K\E[A\E[K\E[?12l\E[?25h\E(B\E[m'
)

_WIZARD_ANIMATE_PRE_RENDERED_NO_COLOR=(
    0.050:$'\n\n\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\nﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n☆ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n･｡ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n☆･*ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n․｡･☆ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\nﾟ․*･｡ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n｡ﾟ․☆･*ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n─*ﾟ․｡･☆ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\nつ─☆ﾟ․*･｡ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n)つ─｡ﾟ․☆･*ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n◕)つ─*ﾟ․｡･☆ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n౪◕)つ─☆ﾟ․*･｡ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n◕౪◕)つ─｡ﾟ․☆･*ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\nつ◕౪◕)つ─*ﾟ․｡･☆ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n(つ◕౪◕)つ─☆ﾟ․*･｡ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n (つ◕౪◕)つ─｡ﾟ․☆･*ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n (つ◕౪◕)つ─*ﾟ․｡･☆ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n (つ◕౪◕)つ─☆ﾟ․*･｡ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n (つ◕౪◕)つ─｡ﾟ․☆･*ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n (つ◕౪◕)つ─*ﾟ․｡･☆ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n (つ◕౪◕)つ─☆ﾟ․*･｡ﾟ\n\n\E[112D\E[A\E[A\E[A\E[A'
    0.050:$'\n\n\n\n\E[112D\E[K\E[A\E[K\E[A\E[K\E[A\E[K\E[A\E[K\E[?12l\E[?25h\E(B\E[m'
)
