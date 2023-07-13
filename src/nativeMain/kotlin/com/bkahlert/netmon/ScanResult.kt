package com.bkahlert.netmon

import Config
import com.bkahlert.exec.ShellScript
import com.bkahlert.io.readLines
import com.bkahlert.io.writeFile

data class ScanResult(
    val hosts: List<Host>,
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
        val upSince = hosts.associate { it.ip to it.firstUp }
        return ScanResult(
            hosts = result.hosts.map { host ->
                host.copy(
                    firstUp = upSince[host.ip] ?: host.firstUp,
                )
            }
        )
    }

    override fun toString(): String = hosts.joinToString("\n") { host ->
        listOf(
            host.ip,
            host.name,
            host.status?.name,
            host.firstUp,
        ).joinToString("\t") { it?.toString() ?: "" }
    }

    companion object {

        enum class TimingTemplate(val value: Int) {
            Paranoid(0), Sneaky(1), Polite(2), Normal(3), Aggressive(4), Insane(5)
        }

        fun get(
            privileged: Boolean = true,
            timingTemplate: TimingTemplate = TimingTemplate.Polite,
            targets: List<String> = Config.targets,
        ): ScanResult = ShellScript(
            """
            nmap \
                -sn ${targets.joinToString(" ") { "'$it'" }} \
                -T${timingTemplate.value} \
                -oG -
            """.trimIndent().let { if (privileged) "sudo $it" else it }
        ).execute()
            .filterNot { it.isBlank() }
            .filterNot { it.startsWith("#") }
            .map(Host::parse)
            .toList()
            .let(::ScanResult)

        fun load(fileName: String = Config.resultFile): ScanResult = kotlin.runCatching {
            readLines(fileName)
                .filterNot { it.isBlank() }
                .filterNot { it.startsWith("#") }
                .map {
                    val parts = it.split("\t")
                        .map { it.takeUnless(String::isBlank) }
                    Host(
                        ip = parts.getOrNull(0),
                        name = parts.getOrNull(1),
                        status = parts.getOrNull(2)?.let { Status.of(it) },
                        firstUp = parts.getOrNull(3)?.toLong(),
                    )
                }
                .toList()
                .let(::ScanResult)
        }.getOrElse {
            println("Error loading scan result: $it")
            ScanResult(emptyList())
        }
    }

    fun save(fileName: String = Config.resultFile) = kotlin.runCatching {
        writeFile(fileName, toString() + "\n")
    }.getOrElse {
        println("Error saving scan result: $it")
    }
}
