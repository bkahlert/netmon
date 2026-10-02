package com.bkahlert.netmon.nmap

import com.bkahlert.netmon.exec.CommandLine
import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.IPv6
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.seconds

class NmapNetworkScanner(
    privileged: Boolean = NmapSettings.privileged,
    val dataDir: Path? = NmapSettings.dataDir,
) {
    // read-only from the outside
    // can internally only be set to false
    var privileged: Boolean by Delegates.vetoable(privileged) { _, _, new -> !new }
        private set

    private val logger by SLF4J
    private val binary: String = requireCommand("nmap").pathString

    fun scan(
        network: Cidr,
        timingTemplate: TimingTemplate = TimingTemplate.Aggressive,
    ): List<Host> {
        logger.info("Scanning network {}", network)

        val nmapCommandLine = CommandLine(binary, buildList {
            dataDir?.also { add("--datadir"); add(it.pathString) }
            if (privileged) add("--privileged")
            add("-T${timingTemplate.value}")
            if (network.ip is IPv6) add("-6")
            add("-sn")
            add("$network")
            add("-oX")
            add("-")
            add("--no-stylesheet")
            add("--noninteractive")
        })

        val xml = kotlin.runCatching {
            nmapCommandLine.exec().readTextOrThrow()
        }.recover { error ->
            val errorMessage = error.message.orEmpty()
            if (errorMessage.contains("exit code 130", ignoreCase = true)) {
                throw InterruptedException("nmap execution cancelled")
            } else if (errorMessage.contains("not permitted", ignoreCase = true)) {
                if (nmapCommandLine.any { it == "--privileged" }) {
                    logger.warn("Insufficient privileges to execute nmap. Switching to unprivileged mode.")
                    privileged = false
                    return scan(network = network, timingTemplate = timingTemplate)
                } else {
                    throw IllegalStateException("Insufficient privileges to execute nmap.", error)
                }
            }
            val retryDelay = 5.seconds
            logger.warn("nmap execution failed. Retrying in $retryDelay.", error)
            Thread.sleep(retryDelay.inWholeMilliseconds)
            nmapCommandLine.exec().readTextOrThrow()
        }.getOrThrow()

        val hosts = NmapXml.parse(xml)
        logger.info("Discovered hosts={} in network={}", hosts, network)
        return hosts
    }
}
