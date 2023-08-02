package com.bkahlert.netmon.mdns

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.NameResolver
import com.bkahlert.netmon.nmap.ProcessCleaner
import com.bkahlert.netmon.nmap.requireCommand
import kotlin.io.path.pathString

/** Reverse name resolver bases on `dig`. */
data object MulticastDnsReverseNameResolver : NameResolver {

    private val logger by SLF4J
    private val binary: String = requireCommand("dig", installationCommand = "dnsutils").pathString
    private val processCleaner = ProcessCleaner()

    /**
     * Resolves the given [ip] to a name.
     *
     * Corresponds to `dig +time=2 +tries=1 @224.0.0.251 -p5353 -x [ip]`.
     */
    override fun resolve(ip: IP): String? {
        logger.debug("Resolving {}", v("ip", ip))

        return CommandLine(binary, buildList {
            add("+time=2")
            add("+tries=1")
            add("@224.0.0.251")
            add("-p5353")
            add("-x")
            add(ip.toString())
        })
            .exec()
            .also { processCleaner.register(it.process) }
            .runCatching {
                readLinesOrThrow()
                    .dropWhile { it != ";; ANSWER SECTION:" }
                    .drop(1)
                    .firstOrNull()
                    ?.split('\t')
                    ?.last()
                    ?.removeSuffix(".")
            }.fold(
                onSuccess = {
                    logger.info("Resolved {} to {}", v("ip", ip), v("name", it))
                    it
                },
                onFailure = {
                    if (it.message?.contains("exit code 9") == true) {
                        logger.info("Resolving {} timed out", v("ip", ip))
                    } else {
                        logger.error("Failed to resolve {}", v("ip", ip), it)
                    }
                    null
                }
            )
    }
}
