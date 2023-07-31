package com.bkahlert.netmon

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

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
                .sorted()
                .map { ip ->
                    val recordedHost = hosts.find { it.ip == ip }
                    val scannedHost = currentResult.hosts.find { it.ip == ip }
                    val newStatus = if (scannedHost != null) scannedHost.status else Status.DOWN
                    Host(
                        ip = ip,
                        name = if (scannedHost != null) scannedHost.name else recordedHost?.name,
                        status = newStatus,
                        since = if (newStatus != recordedHost?.status) currentResult.timestamp else recordedHost?.since,
                        model = if (scannedHost != null) scannedHost.model else recordedHost?.model,
                        services = scannedHost?.services ?: (recordedHost?.services ?: emptyList()),
                    ).also {
                        if (it != recordedHost) onChange(it)
                    }
                },
            timestamp = currentResult.timestamp,
        )
    }

    fun save(
        file: Path,
        format: StringFormat = JsonFormat,
    ) = kotlin.runCatching {
        file.writeText(format.encodeToString(this))
    }.getOrElse { error ->
        logger.error("Error saving scan result", error)
    }

    enum class TimingTemplate(val value: Int) {
        Paranoid(0), Sneaky(1), Polite(2), Normal(3), Aggressive(4), Insane(5)
    }

    companion object {
        private val logger by SLF4J

        fun load(
            file: Path,
            format: StringFormat = JsonFormat,
        ): ScanResult? = file.takeIf { it.exists() }?.runCatching {
            format.decodeFromString<ScanResult>(readText())
        }?.getOrElse { error ->
            logger.error("Error loading scan result", error)
            null
        }
    }
}
