package com.bkahlert.netmon

import Defaults
import com.bkahlert.exec.ShellScript
import com.bkahlert.exec.checkCommand
import com.bkahlert.io.File
import com.bkahlert.io.Logger
import com.bkahlert.serialization.JsonFormat
import com.bkahlert.time.timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

fun interface NetworkScanner {
    fun scan(network: Cidr): ScanResult
}

@Serializable
data class ScanResult(
    @SerialName("network") val network: Cidr,
    @SerialName("hosts") val hosts: List<Host>,
    @SerialName("timestamp") val timestamp: Long = timestamp(),
) {
    fun diff(result: ScanResult) = sequence {
        val newIps = result.hosts.map { it.ip }
        val removedHosts = hosts.filter { it.ip !in newIps }
        removedHosts.forEach { yield(ScanEvent.HostDownEvent(it)) }

        val oldIps = hosts.map { it.ip }
        val addedHosts = result.hosts.filter { it.ip !in oldIps }
        addedHosts.forEach { yield(ScanEvent.HostUpEvent(it)) }
    }

    fun merge(result: ScanResult): ScanResult {
        check(network == result.network) { "Networks do not match: $network != ${result.network}" }
        val upSince = hosts.associate { it.ip to it.firstUp }
        return ScanResult(
            network = network,
            hosts = result.hosts.map { host ->
                host.copy(
                    firstUp = upSince[host.ip] ?: host.firstUp,
                )
            }
        )
    }

    companion object {

        enum class TimingTemplate(val value: Int) {
            Paranoid(0), Sneaky(1), Polite(2), Normal(3), Aggressive(4), Insane(5)
        }

        fun load(
            file: File = Defaults.resultFile,
            format: StringFormat = JsonFormat,
        ): ScanResult? = file.takeIf { it.exists() }?.runCatching {
            format.decodeFromString<ScanResult>(readText())
        }?.getOrElse { error ->
            Logger.error("Error loading scan result: $error")
            null
        }
    }

    fun save(
        file: File = Defaults.resultFile,
        format: StringFormat = JsonFormat,
    ) = kotlin.runCatching {
        file.writeText(format.encodeToString(this))
    }.getOrElse {
        Logger.error("Error saving scan result: $it")
    }
}


data class NmapNetworkScanner(
    val privileged: Boolean = Defaults.privileged,
    val timingTemplate: ScanResult.Companion.TimingTemplate = ScanResult.Companion.TimingTemplate.Normal,
) : NetworkScanner {

    init {
        checkCommand("nmap")
    }

    override fun scan(network: Cidr): ScanResult {
        Logger.debug("Scanning network $network")

        val cmdline = buildList {
            if (privileged) add("sudo")
            add("nmap")
            add("-sn")
            add("'$network'")
            add("-T${timingTemplate.value}")
            add("-oG")
            add("-")
        }

        return ShellScript(cmdline.joinToString(" "))
            .execute()
            .filterNot { it.isBlank() }
            .filterNot { it.startsWith("#") }
            .map(Host::parse)
            .toList()
            .let { ScanResult(network, it) }
    }
}
