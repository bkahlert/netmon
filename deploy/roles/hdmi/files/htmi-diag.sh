#!/usr/bin/env bash

# Prints some HDMI related diagnostics

sudo apt-get install -y edid-decode
tvservice -d edit.dat

printf "\n\033[1mDecoded EDID\033[0m\n" >&2
edid-decode edit.dat

printf "\n\033[1mCurrent state of the outputs\033[0m\n" >&2
DISPLAY=:0 xrandr

printf "\n\033[1mFurther debugging:\033[0m\n" >&2
printf "  - read %s\n" 'https://www.raspberrypi.com/documentation/computers/config_txt.html#which-values-are-valid-for-my-monitor' >&2
printf "  - check the hdmi_* options in %s\n" /boot/config.txt >&2
printf "  - set resolutions manually: \033[3m%s\033[23m\n" /boot/config.txt >&2
