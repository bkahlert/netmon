package com.bkahlert.netmon

import com.bkahlert.kommons.Program
import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.time.Now
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.appendLines
import kotlin.io.path.pathString
import kotlin.io.path.writeText

data class NmapNetworkScanner(
    val privileged: Boolean = Defaults.privileged,
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
            var ip: String? = null
            var host: String? = null
            var status: Status? = null
            line.split("\t")
                .map { it.split(": ", limit = 2) }
                .forEach {
                    when (it.first().lowercase()) {
                        "host" -> {
                            ip = it.last().split(" ").first()
                            host = it.last().split(" ").last().removeSurrounding("(", ")").takeUnless { it.isBlank() }
                        }

                        "status" -> status = Status.of(it.last())
                    }
                }
            return Host(
                ip = ip,
                name = host,
                status = status,
                firstUp = if (status == Status.UP) Now else null
            )
        }
    }
}

private class ProcessCleaner(
    private val logFile: Path = Paths.get(".netmon.shutdown.log"),
) {
    private val lock = ReentrantLock()
    private var processes = emptyList<Process>()

    init {
        logFile.writeText("")
        Program.onExit { destroyAll() }
    }

    private fun log(message: String): Path = logFile.appendLines(listOf(message))

    private fun destroyAll() {
        processes.forEach { destroy(it) }
    }

    private fun destroy(process: Process) {
        if (process.isAlive) {
            try {
                process.destroyForcibly()
                log("Process $process was destroyed forcibly.")
            } catch (e: Exception) {
                log("Process $process failed to destroy forcibly: $e")
            }
        }
    }

    fun register(process: Process) {
        lock.withLock {
            processes = processes.filter { it.isAlive } + process
        }
    }
}
