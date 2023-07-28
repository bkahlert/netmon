package com.bkahlert.netmon

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.time.Now
import kotlin.io.path.pathString

data class NmapNetworkScanner(
    val privileged: Boolean = Settings.privileged,
    val timingTemplate: ScanResult.TimingTemplate = ScanResult.TimingTemplate.Normal,
) : NetworkScanner {

    private val logger by SLF4J
    private val binary: String = requireCommand("nmap").pathString
    private val processCleaner = ProcessCleaner()

    override fun scan(network: Cidr): ScanResult {
        logger.debug("Scanning network $network")

        return CommandLine(if (privileged) "sudo" else binary, buildList {
            if (privileged) add(binary)
            add("-sn")
            add("$network")
            add("-T${timingTemplate.value}")
            add("-oG")
            add("-")
        })
            .exec()
            .also { processCleaner.register(it.process) }
            .readLinesOrThrow()
            .filterNot { it.isBlank() }
            .filterNot { it.startsWith("#") }
            .map(NmapNetworkScanner::parseHost)
            .toList()
            .let {
                ScanResult(
                    network = network,
                    hosts = it,
                    timestamp = Now
                )
            }
    }

    companion object {
        fun parseHost(line: String): Host {
            var ip: IP? = null
            var host: String? = null
            var status: Status? = null
            line.split("\t")
                .map { it.split(": ", limit = 2) }
                .forEach {
                    when (it.first().lowercase()) {
                        "host" -> {
                            ip = IP(it.last().split(" ").first())
                            host = it.last().split(" ").last().removeSurrounding("(", ")").takeUnless { it.isBlank() }
                        }

                        "status" -> status = Status.of(it.last())
                    }
                }
            return Host(
                ip = checkNotNull(ip) { "IP not found in line: $line" },
                name = host,
                status = status,
                since = null,
            )
        }
    }
}
