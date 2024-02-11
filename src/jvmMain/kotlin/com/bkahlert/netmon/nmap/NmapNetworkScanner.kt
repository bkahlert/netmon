package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.serialization.JsonFormat
import java.net.Inet6Address
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
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
        logger.info("Scanning network {}", v("network", network))

        val nmapCommandLine = CommandLine(binary, buildList {
            dataDir?.also { add("--datadir"); add(it.pathString) }
            if (privileged) add("--privileged")
            add("-T${timingTemplate.value}")
            if (network.ip.addr is Inet6Address) add("-6")
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

        val json = XmlToJsonConverter.convert(xml)
        val hosts = JsonFormat.decodeFromString<NmapOutput>(json).nmapRun.hosts
        logger.info("Discovered {} in {}", kv("hosts", hosts), kv("network", network))
        return hosts
    }
}

object XmlToJsonConverter {

    private val logger by SLF4J
    private val python: String = requireCommand("python3").pathString
    private val xml2json: String = run {
        val resource: URL = XmlToJsonConverter::class.java.classLoader.getResource("xml2json.py") ?: error("Resource not found: xml2json.py")
        resource.readBytes().let {
            val tempFile = createTempFile("xml2json", ".py").apply { toFile().deleteOnExit() }
            tempFile.writeBytes(it)
            tempFile.pathString
        }
    }

    fun convert(xml: String): String = createTempFile("xml2json", ".xml").let {
        it.writeText(xml)
        val result = kotlin.runCatching { convert(it) }
        it.deleteIfExists()
        result.getOrThrow()
    }

    fun convert(xmlFile: Path): String = CommandLine(python, xml2json, "--type", "xml2json", xmlFile.pathString)
        .exec()
        .runCatching {
            readTextOrThrow()
        }.onFailure {
            val xml = runCatching { xmlFile.readText() }.getOrElse { "—FAIL—" }
            logger.error("Failed to convert XML to JSON: {}", kv("xml", xml))
        }.getOrThrow()
        .trim()
}
