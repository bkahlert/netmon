package com.bkahlert.netmon

enum class Status {
    UP, DOWN, UNKNOWN;

    companion object {
        fun of(value: String): Status =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
