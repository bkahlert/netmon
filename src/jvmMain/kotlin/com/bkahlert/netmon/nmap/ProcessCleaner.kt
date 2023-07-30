package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.Program
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.appendLines
import kotlin.io.path.writeText

class ProcessCleaner(
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
        lock.withLock {
            processes
                .mapNotNull { destroy(it) }
                .also {
                    log("${it.size} process(es) cleaned up.")
                }
        }
    }

    private fun destroy(process: Process) = process.takeIf { it.isAlive }?.apply {
        try {
            process.destroyForcibly()
            log("Process $process was destroyed forcibly.")
        } catch (e: Exception) {
            log("Process $process failed to destroy forcibly: $e")
        }
    }

    fun register(process: Process) {
        lock.withLock {
            processes = processes.filter { it.isAlive } + process
        }
    }
}
