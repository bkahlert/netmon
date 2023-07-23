package com.bkahlert.sequences

fun Sequence<String>.splitToLines(): Sequence<String> {
    val buffer = StringBuilder()
    return sequence {
        this@splitToLines.forEach { chunk ->
            chunk.forEach { char ->
                if (char == '\n') {
                    yield(buffer.toString())
                    buffer.clear()
                } else {
                    buffer.append(char)
                }
            }
        }
        if (buffer.isNotEmpty()) {
            yield(buffer.toString())
        }
    }
}
