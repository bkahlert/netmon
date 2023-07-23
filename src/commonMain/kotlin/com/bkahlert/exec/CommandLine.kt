package com.bkahlert.exec

import com.bkahlert.term.bold

expect class CommandLine(
    command: String,
    args: List<String>,
) {
    constructor(command: String, vararg args: String)

    fun isOnPath(): Boolean
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
