package com.bkahlert.io

import com.bkahlert.sequences.splitToLines
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite

fun readFile(fileName: String) = sequence {
    val file = fopen(fileName, "r") ?: error("Cannot open input file $fileName")
    try {
        memScoped {
            val bufferLength = 64 * 1024
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

fun readLines(fileName: String) = readFile(fileName).splitToLines()

fun writeFile(fileName: String, content: String) {
    val file = fopen(fileName, "w") ?: error("Cannot open output file $fileName")
    try {
        val cString = content.cstr
        fwrite(cString.getPointer(scope = MemScope()), 1.convert(), cString.size.convert(), file)
    } finally {
        fclose(file)
    }
}
