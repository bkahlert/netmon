package com.bkahlert.netmon

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

    fun merge(currentResult: ScanResult): ScanResult {
        check(network == currentResult.network) { "Networks do not match: $network != ${currentResult.network}" }
        return ScanResult(
            network = network,
            hosts = buildSet {
                hosts.forEach { add(it.ip) }
                currentResult.hosts.forEach { add(it.ip) }
            }.sortedBy { ipv4ToInt(it) }.map { ip ->
                val recordedHost = hosts.find { it.ip == ip }
                val scannedHost = currentResult.hosts.find { it.ip == ip }
                val newStatus = if (scannedHost != null) scannedHost.status else Status.DOWN
                Host(
                    ip = ip,
                    name = if (scannedHost != null) scannedHost.name else recordedHost?.name,
                    status = newStatus,
                    since = if (newStatus != recordedHost?.status) currentResult.timestamp else recordedHost?.since,
                )
            },
            timestamp = currentResult.timestamp,
        )
    }

    fun diff(newResult: ScanResult): Sequence<ScanEvent> = sequence {
        newResult.hosts.filterNot { it in hosts }.forEach { host ->
            if (host.status == Status.DOWN) yield(ScanEvent.HostDownEvent(host))
            else yield(ScanEvent.HostUpEvent(host))
        }
    }

    companion object;

    enum class TimingTemplate(val value: Int) {
        Paranoid(0), Sneaky(1), Polite(2), Normal(3), Aggressive(4), Insane(5)
    }
}
