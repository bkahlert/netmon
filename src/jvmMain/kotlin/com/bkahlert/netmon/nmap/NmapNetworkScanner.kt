package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.kommons.text.CodePoint.Companion.codePoints
import com.bkahlert.kommons.text.truncateEnd
import com.bkahlert.kommons.text.truncateStart
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.NameResolver
import com.bkahlert.netmon.NetworkScanSettings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.TimingTemplate
import com.bkahlert.netmon.nmap.NmapOutput.Host.Address.AttrType
import java.net.Inet6Address
import java.net.URL
import kotlin.io.path.createTempFile
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

//nmap --privileged -sU -p137 --script nbstat 192.168.16.0/24
//nmap --privileged -sU -p137 --script nbstat 192.168.16.10 -oX -
data class NmapNetworkScanner(
    val privileged: Boolean = NetworkScanSettings.privileged,
) : NameResolver {

    private val logger by SLF4J
    private val binary: String = requireCommand("nmap").pathString
    private val python: String = requireCommand("python3").pathString
    private val xml2json: String = run {
        val java: Class<NmapNetworkScanner> = NmapNetworkScanner::class.java
        val resource: URL = java.classLoader.getResource("xml2json.py") ?: error("Resource not found: xml2json.py")
        resource.readBytes().let {
            val tempFile = createTempFile("xml2json", ".py")
            tempFile.writeBytes(it)
            tempFile.pathString
        }
    }

    fun scan(
        network: Cidr,
        timingTemplate: TimingTemplate = TimingTemplate.Aggressive,
    ): List<NmapResult> {
        logger.info("Scanning network {}", v("network", network))

        val nmapCommandLine = CommandLine(binary, buildList {
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

        return nmapOutput(nmapCommandLine)
            .let { (nmapRun) ->
                nmapRun.host.orEmpty().mapNotNull(NmapResult::from)
            }
            .also { logger.info("Discovered {} in {}", kv("hosts", it), kv("network", network)) }
    }

    override fun resolve(
        ip: IP,
    ): String? = resolve(ip, TimingTemplate.Aggressive)

    fun resolve(
        ip: IP,
        timingTemplate: TimingTemplate,
    ): String? {
        logger.debug("Resolving {}", v("ip", ip))

        val nmapCommandLine = CommandLine(binary, buildList {
            if (privileged) add("--privileged")
            add("-T${timingTemplate.value}")
            if (ip.addr is Inet6Address) add("-6")
            if (privileged) add("-sU") else add("-sT")
            add("--script")
            add("nbstat.nse") // cat /usr/share/nmap/scripts/nbstat.nse
            add("-p")
            if (privileged) add("U:137") else add("T:445")
            add("$ip")
            add("-oX")
            add("-")
            add("--no-stylesheet")
            add("--noninteractive")
        })

        return nmapOutput(nmapCommandLine)
            .let { (nmapRun) ->
                val hostscript = nmapRun.host.orEmpty().firstOrNull()?.hostscript
                val result = hostscript?.script?.output?.lineSequence()?.firstOrNull { it.startsWith("NetBIOS name:") }
                result?.substringAfter(":")?.substringBefore(",")?.trim()
            }
            .also {
                if (it != null) logger.info("Resolved {} to {}", v("ip", ip), v("name", it))
                else logger.debug("Resolving {} timed out", v("ip", ip))
            }
    }

    private fun nmapOutput(commandLine: CommandLine): NmapOutput {
        // TODO write XML to file instead of reading it into memory and then writing it to file
        val xml = kotlin.runCatching { commandLine.exec().readTextOrThrow() }
            .recover { error ->
                if (error.message.orEmpty().contains("not permitted", ignoreCase = true)) {
                    throw IllegalStateException("Insufficient privileges to execute nmap.", error)
                }
                Thread.sleep(5.seconds.inWholeMilliseconds)
                commandLine.exec().readTextOrThrow()
            }
            .getOrThrow()
            .trim()
        check(xml.isNotBlank()) { "nmap output is blank" }
        check(xml.startsWith("<")) { "nmap output does not start with '<', but with '${xml.truncateEnd(10.codePoints)}'" }
        check(xml.endsWith(">")) { "nmap output does not end with '>', but with '${xml.truncateStart(10.codePoints)}'" }

        val json = CommandLine(python, xml2json, "-t", "xml2json").exec(customize = {
            val xmlFile = createTempFile("nmap", ".xml")
                .also { it.writeText(xml) }
                .toFile()
                .also { it.deleteOnExit() }
            redirectInput(xmlFile)
        })
            .readTextOrThrow()
            .also { logger.debug("Decoding nmap output: {}", kv("output", it)) }

        return JsonFormat.decodeFromString<NmapOutput>(json)
    }

    data class NmapResult(
        val ip: IP,
        val name: String?,
        val vendor: String?,
        val status: Status?,
    ) {
        companion object {
            fun from(host: NmapOutput.Host): NmapResult? {
                val ip = host.address.firstOrNull { addr ->
                    addr.addrType == AttrType.ipv4 || addr.addrType == AttrType.ipv6
                }?.let { IP(it.addr) } ?: return null

                val vendor = host.address.filter { addr ->
                    addr.addrType == AttrType.mac
                }.firstOrNull()?.vendor

                return NmapResult(
                    ip = ip,
                    name = host.hostnames?.hostname?.name,
                    vendor = vendor,
                    status = Status.of(host.status.state.name),
                )
            }
        }
    }

    companion object
}
