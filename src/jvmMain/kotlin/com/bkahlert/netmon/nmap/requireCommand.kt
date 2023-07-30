package com.bkahlert.netmon.nmap

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isExecutable

fun requireCommand(
    command: String,
    installationPackage: String? = command,
    installationCommand: String? = installationPackage?.let {
        "sudo apt-get update && sudo apt-get install -y $it"
    },
): Path = checkNotNull(
    System.getenv("PATH").split(":").firstNotNullOfOrNull { path ->
        Paths.get(path, command).takeIf { it.isExecutable() }
    }) {
    buildString {
        appendLine("Command $command not found.")
        if (installationCommand != null) {
            appendLine("Please install $command, e.g. using:")
            appendLine(installationCommand)
        } else {
            appendLine("Please install $command.")
        }
    }
}
