package com.bkahlert.io

import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.useLines
import kotlin.io.path.writeText

actual data class File actual constructor(val path: String) {

    actual fun exists(): Boolean = Paths.get(path).exists()

    actual fun readChunks(
        bufferLength: Int,
    ) = sequence {
        Paths.get(path).useLines { lines ->
            lines.forEach { line ->
                yield(line)
            }
        }
    }

    actual fun writeText(text: String) {
        Paths.get(path).writeText(text)
    }
}
