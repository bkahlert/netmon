package com.bkahlert.netmon

import com.bkahlert.kommons.logging.SLF4J
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun interface NetworkScanner {
    fun scan(network: Cidr): ScanResult
}

@Serializable
data class ScanResult(
    @SerialName("network") val network: Cidr,
    @SerialName("hosts") val hosts: List<Host>,
    @SerialName("timestamp") val timestamp: Instant,
) {
    private val logger by SLF4J

    fun diff(newResult: ScanResult): Sequence<ScanEvent> = sequence {
        val newIps = newResult.hosts.map { it.ip }
        val removedHosts = hosts.filter { it.ip !in newIps }.map { it.copy(status = Status.DOWN) }
        removedHosts.forEach { yield(ScanEvent.HostDownEvent(it)) }

        val oldIps = hosts.map { it.ip }
        val addedHosts = newResult.hosts.filter { it.ip !in oldIps }
        addedHosts.forEach { yield(ScanEvent.HostUpEvent(it)) }
    }

    fun merge(newResult: ScanResult): ScanResult {
        check(network == newResult.network) { "Networks do not match: $network != ${newResult.network}" }
        val firstUps: Map<String?, Instant?> = hosts.associate { it.ip to it.firstUp }
        return ScanResult(
            network = network,
            hosts = newResult.hosts.map { host ->
                host.copy(
                    firstUp = firstUps[host.ip] ?: host.firstUp,
                )
            },
            timestamp = newResult.timestamp,
        )
    }

    companion object;

    enum class TimingTemplate(val value: Int) {
        Paranoid(0), Sneaky(1), Polite(2), Normal(3), Aggressive(4), Insane(5)
    }
}
