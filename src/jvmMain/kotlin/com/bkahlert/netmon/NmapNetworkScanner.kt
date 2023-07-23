package com.bkahlert.netmon

import com.bkahlert.io.Logger
import com.bkahlert.kommons.exec.CommandLine
import kotlin.io.path.pathString

data class NmapNetworkScanner(
    val privileged: Boolean = Defaults.privileged,
    val timingTemplate: ScanResult.TimingTemplate = ScanResult.TimingTemplate.Normal,
) : NetworkScanner {

    val binary: String = requireCommand("nmap").pathString

    override fun scan(network: Cidr): ScanResult {
        Logger.debug("Scanning network $network")

        return CommandLine(if (privileged) "sudo" else binary, buildList {
            if (privileged) add(binary)
            add("-sn")
            add("$network")
            add("-T${timingTemplate.value}")
            add("-oG")
            add("-")
        })
            .exec()
            .readLinesOrThrow()
            .filterNot { it.isBlank() }
            .filterNot { it.startsWith("#") }
            .map(Host.Companion::parse)
            .toList()
            .let { ScanResult(network, it) }
    }
}
