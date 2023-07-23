package com.bkahlert.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CValues
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

actual data class File actual constructor(val path: String) {

    actual fun exists(): Boolean = access(path, F_OK) != -1

    actual fun readChunks(
        bufferLength: Int,
    ) = sequence {
        val file = fopen(path, "r") ?: error("Cannot open input file $path")
        try {
            memScoped {
                val buffer = allocArray<ByteVar>(bufferLength)
                val readBytes = fread(buffer, 1.convert(), bufferLength.convert(), file)
                if (readBytes > 0u) {
                    yield(buffer.readBytes(readBytes.toInt()).decodeToString().removeSuffix("\u0000"))
                }
            }
        } finally {
            fclose(file)
        }
    }

    actual fun writeText(text: String) {
        val file = fopen(path, "w") ?: error("Cannot open output file $path")
        try {
            val cString: CValues<ByteVarOf<Byte>> = text.cstr
            fwrite(
                cString.getPointer(scope = MemScope()),
                1.convert(),
                cString.size
                    .minus(1) // drop trailing NUL
                    .convert(),
                file
            )
        } finally {
            fclose(file)
        }
    }
}
