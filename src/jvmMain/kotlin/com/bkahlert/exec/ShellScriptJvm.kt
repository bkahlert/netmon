package com.bkahlert.exec

import com.bkahlert.io.Logger
import com.bkahlert.sequences.splitToLines

actual class ShellScript actual constructor(
    private val script: String,
) {
    actual fun execute(): Sequence<String> = sequence {
        Logger.debug("Executing: $script")
        ProcessBuilder("/bin/bash", "-c", script).start().inputStream.bufferedReader().useLines { yieldAll(it) }
    }.splitToLines()
}
