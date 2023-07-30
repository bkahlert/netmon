package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.ScanResult
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import kotlin.io.path.pathString

data class NmapNetworkScanner(
    val privileged: Boolean = Settings.privileged,
    val timingTemplate: ScanResult.TimingTemplate = ScanResult.TimingTemplate.Normal,
) {

    private val logger by SLF4J
    private val binary: String = requireCommand("nmap").pathString
    private val processCleaner = ProcessCleaner()

    fun scan(network: Cidr): List<NmapResult> {
        logger.info("Scanning network $network")

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
            .map(NmapResult::parse)
            .toList()
            .also { logger.info("Discovered {} in {}", kv("hosts", it), kv("network", network)) }
    }

    data class NmapResult(
        val ip: IP,
        val name: String?,
        val status: Status?,
    ) {
        companion object {
            fun parse(line: String): NmapResult {
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
                return NmapResult(
                    ip = checkNotNull(ip) { "IP not found in line: $line" },
                    name = host,
                    status = status,
                )
            }
        }
    }

    companion object
}
