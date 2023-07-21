package com.bkahlert.exec

import com.bkahlert.io.Logger
import com.bkahlert.sequences.splitToLines
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

class ShellScript(
    private val script: String,
) {
    fun execute(): Sequence<String> = sequence {
        Logger.debug("Executing: $script")
        val fp = popen(script, "r") ?: error("Failed to run command")
        try {
            val buffer = ByteArray(4096)
            while (true) {
                val input = fgets(buffer.refTo(0), buffer.size, fp)?.toKString()
                if (input.isNullOrEmpty()) break
                yield(input)
            }
        } finally {
            pclose(fp)
        }
    }.splitToLines()
}
