package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.exec.CommandLine
import com.bkahlert.kommons.exec.ShellScript
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.IP
import com.bkahlert.netmon.JsonFormat
import com.bkahlert.netmon.NameResolver
import com.bkahlert.netmon.ScanResult
import com.bkahlert.netmon.Settings
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.nmap.NmapOutput.Host.Address.AttrType
import java.net.URL
import kotlin.io.path.createTempFile
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

// sudo nmap -sU -p137 --script nbstat 192.168.16.0/24
//sudo nmap -sU -p137 --script nbstat 192.168.16.10 -oX -
data class NmapNetworkScanner(
    val privileged: Boolean = Settings.Scanner.PRIVILEGED_SCAN,
    val timingTemplate: ScanResult.TimingTemplate = ScanResult.TimingTemplate.Normal,
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
    private val processCleaner = ProcessCleaner()

    fun scan(network: Cidr): List<NmapResult> {
        logger.info("Scanning network {}", v("network", network))

        val nmapCommandLine = CommandLine(if (privileged) "sudo" else binary, buildList {
            if (privileged) add(binary)
            add("-sn")
            add("$network")
            add("-T${timingTemplate.value}")
            add("-oX")
            add("-")
        })

        return ShellScript("$nmapCommandLine | '$python' '$xml2json' -t xml2json")
            .exec()
            .also { processCleaner.register(it.process) }
            .readTextOrThrow()
            .let {
                val output = JsonFormat.decodeFromString<NmapOutput>(it)
                output.nmapRun.host.orEmpty().mapNotNull(NmapResult::from)
            }
            .also { logger.info("Discovered {} in {}", kv("hosts", it), kv("network", network)) }
    }

    override fun resolve(ip: IP): String? {
        logger.info("Resolving {}", v("ip", ip))

        if (!privileged) {
            logger.warn("Failed to resolve {} as that requires root privileges", v("ip", ip))
            return null
        }

        val nmapCommandLine = CommandLine("sudo", buildList {
            add(binary)
            add("-sU")
            add("--script")
            add("nbstat")
            add("-p")
            add("137")
            add("$ip")
            add("-oX")
            add("-")
        })

        return ShellScript("$nmapCommandLine | '$python' '$xml2json' -t xml2json")
            .exec()
            .also { processCleaner.register(it.process) }
            .readTextOrThrow()
            .let {
                val output = JsonFormat.decodeFromString<NmapOutput>(it)
                val hostscript = output.nmapRun.host.orEmpty().firstOrNull()?.hostscript
                val result = hostscript?.script?.output?.lineSequence()?.firstOrNull { it.startsWith("NetBIOS name:") }
                result?.substringAfter(":")?.substringBefore(",")?.trim()
            }
            .also {
                if (it != null) logger.info("Resolved {} to {}", v("ip", ip), v("name", it))
                else logger.info("Resolving {} timed out", v("ip", ip))
            }
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
