package com.bkahlert.exec

import kotlinx.cinterop.toKString
import platform.posix.X_OK
import platform.posix.access
import platform.posix.getenv

actual class CommandLine actual constructor(
    val command: String,
    val args: List<String>,
) {
    actual constructor(command: String, vararg args: String) : this(command, args.asList())

    actual fun isOnPath(): Boolean {
        val path = getenv("PATH")?.toKString()
        return path?.split(":")?.any { dir ->
            val filePath = "$dir/$command"
            access(filePath, X_OK) == 0
        } ?: false
    }
}
