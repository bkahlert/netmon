#!/usr/bin/env bash

# Prints some HDMI related diagnostics

sudo apt-get install -y edid-decode
tvservice -d edit.dat

printf "\n\e[1mDecoded EDID\e[0m\n" >&2
edid-decode edit.dat

printf "\n\e[1mCurrent state of the outputs\e[0m\n" >&2
DISPLAY=:0 xrandr

{
    printf "\n\e[4mUseful commands:\e[0m\n"
    printf -- "- read %s\n" 'https://www.raspberrypi.com/documentation/computers/config_txt.html#which-values-are-valid-for-my-monitor'
    printf -- "- check the hdmi_* options in %s\n" /boot/config.txt
    printf -- "- set resolutions manually: \e[3m%s\e[23m\n" /boot/config.txt
} | sed 's/^/  /' >&2
