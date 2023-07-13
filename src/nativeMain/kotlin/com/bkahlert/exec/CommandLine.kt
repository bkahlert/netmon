package com.bkahlert.exec

import com.bkahlert.term.bold
import kotlinx.cinterop.toKString
import platform.posix.X_OK
import platform.posix.access
import platform.posix.getenv

class CommandLine(
    val command: String,
    val args: List<String>,
) {
    constructor(command: String, vararg args: String) : this(command, args.asList())

    fun isOnPath(): Boolean {
        val path = getenv("PATH")?.toKString()
        return path?.split(":")?.any { dir ->
            val filePath = "$dir/$command"
            access(filePath, X_OK) == 0
        } ?: false
    }
}

fun checkCommand(
    command: String,
    installationPackage: String? = command,
    installationCommand: String? = installationPackage?.let {
        "sudo apt-get update && sudo apt-get install -y $it"
    },
) {
    check(CommandLine(command).isOnPath()) {
        buildString {
            appendLine("Command $command not found.")
            if (installationCommand != null) {
                appendLine("Please install $command, e.g. using:")
                appendLine(installationCommand.bold())
            } else {
                appendLine("Please install $command.")
            }
        }
    }
}
