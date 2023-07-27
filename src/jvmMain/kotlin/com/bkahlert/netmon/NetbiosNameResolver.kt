package com.bkahlert.netmon

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import kotlin.io.path.pathString

class NetbiosNameResolver : NameResolver {

    private val logger by SLF4J
    private val binary: String = requireCommand("nbtscan").pathString
    private val processCleaner = ProcessCleaner()

    override fun resolve(ip: IP): String? {
        logger.debug("Resolving {}", v("ip", ip))

        return CommandLine(binary, "-q", "-s", ";", ip.toString())
            .exec()
            .also { processCleaner.register(it.process) }
            .readLinesOrThrow()
            .firstNotNullOfOrNull { line ->
                val columns = line.split(";")
                columns.getOrNull(1)?.takeIf { it.isNotBlank() }
            }
    }
}
