package com.bkahlert.io

import com.bkahlert.sequences.splitToLines

expect class File(path: String) {

    fun exists(): Boolean

    fun readChunks(
        bufferLength: Int = 64 * 1024,
    ): Sequence<String>


    fun writeText(text: String)
}

fun File.readText() = readChunks().joinToString(separator = "")
fun File.readLines() = readChunks().splitToLines()

// TODO: did run the following on ssh 10.0.0.2
// sudo apt-get update
// sudo apt-get install libudev-dev libasound2-dev libdbus-1-dev fcitx-libs-dev
// wget https://www.libsdl.org/release/SDL2-2.28.1.tar.gz
// tar xvf SDL2-2.28.1.tar.gz
// cd SDL2-2.28.1
// export VIDEO_RPI=1
// ./configure --disable-video-x11 --disable-video-opengl --enable-video-rpi
// make
// TODO: continue here; build needed because of missing RPI driver
// sudo make install
// pip3 install PySDL2 (not using apt-get; would install sdl2 again)
// TODO: restart and hope show-image.py displays something

// https://www.libsdl.org/release/SDL2-2.28.1.tar.gz
