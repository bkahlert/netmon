#!/bin/bash

# THIS IS A SHAMELESS COPY OF THE start.sh FILE FROM PISIGNAGE
# IN ORDER TO ADD SERVICE DEPENDENCIES

unclutter -idle 5 -root &
sudo rfkill unblock wifi
cd /home/pi/piSignagePro

# THIS LINE WAS ADDED
sudo /home/pi/wait-for-netmon.sh

. /home/pi/.bash_profile
export WEBKIT_DISABLE_TBS=1
node pi-monitor.js
sleep 10
sudo kill $(pidof python omx.py)
sudo pkill omxplayer
