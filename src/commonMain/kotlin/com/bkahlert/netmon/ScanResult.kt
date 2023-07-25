package com.bkahlert.netmon

import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScanResult(
    @SerialName("network") val network: Cidr,
    @SerialName("hosts") val hosts: List<Host>,
    @SerialName("timestamp") @Serializable(InstantAsEpochSecondsSerializer::class) val timestamp: Instant,
) {

    fun merge(
        currentResult: ScanResult,
        onChange: (Host) -> Unit = {},
    ): ScanResult {
        check(network == currentResult.network) { "Networks do not match: $network != ${currentResult.network}" }
        return ScanResult(
            network = network,
            hosts = buildSet {
                hosts.forEach { add(it.ip) }
                currentResult.hosts.forEach { add(it.ip) }
            }
                .sortedBy { it.intValue }
                .map { ip ->
                    val recordedHost = hosts.find { it.ip == ip }
                    val scannedHost = currentResult.hosts.find { it.ip == ip }
                    val newStatus = if (scannedHost != null) scannedHost.status else Status.DOWN
                    Host(
                        ip = ip,
                        name = if (scannedHost != null) scannedHost.name else recordedHost?.name,
                        status = newStatus,
                        since = if (newStatus != recordedHost?.status) currentResult.timestamp else recordedHost?.since,
                    ).also {
                        if (it != recordedHost) onChange(it)
                    }
                },
            timestamp = currentResult.timestamp,
        )
    }

    companion object;

    enum class TimingTemplate(val value: Int) {
        Paranoid(0), Sneaky(1), Polite(2), Normal(3), Aggressive(4), Insane(5)
    }
}
