package com.bkahlert.io

import com.bkahlert.sequences.splitToLines
import kotlinx.cinterop.ByteVar
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
            val cString = text.cstr
            fwrite(cString.getPointer(scope = MemScope()), 1.convert(), cString.size.convert(), file)
        } finally {
            fclose(file)
        }
    }
}
