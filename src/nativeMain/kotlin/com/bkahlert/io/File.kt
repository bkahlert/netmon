package com.bkahlert.io

import com.bkahlert.sequences.splitToLines
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

data class File(val path: String) {

    fun exists(): Boolean = access(path, F_OK) != -1

    fun readChunks(
        bufferLength: Int = 64 * 1024,
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

    fun readText() = readChunks().joinToString(separator = "")

    fun readLines() = readChunks().splitToLines()

    fun writeText(text: String) {
        val file = fopen(path, "w") ?: error("Cannot open output file $path")
        try {
            val cString: CValues<ByteVarOf<Byte>> = text.cstr
            fwrite(
                __ptr = cString.getPointer(scope = MemScope()),
                __size = 1.convert(),
                __nitems = cString.size
                    .minus(1) // drop trailing NUL
                    .convert(),
                __stream = file
            )
        } finally {
            fclose(file)
        }
    }
}
