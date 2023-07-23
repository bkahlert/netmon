package com.bkahlert.netmon

import com.bkahlert.time.timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    companion object;

    enum class TimingTemplate(val value: Int) {
        Paranoid(0), Sneaky(1), Polite(2), Normal(3), Aggressive(4), Insane(5)
    }
}
