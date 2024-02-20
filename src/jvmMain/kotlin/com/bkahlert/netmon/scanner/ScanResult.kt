package com.bkahlert.netmon.scanner

import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.v
import com.bkahlert.kommons.time.InstantAsEpochSecondsSerializer
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.serialization.JsonFormat
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
    @SerialName("interface") val `interface`: String,
    @SerialName("cidr") val cidr: Cidr,
    @SerialName("hosts") val hosts: List<Host>,
    @SerialName("timestamp") @Serializable(InstantAsEpochSecondsSerializer::class) val timestamp: Instant,
) {

    fun merge(
        currentResult: ScanResult,
        onChange: (Host) -> Unit = {},
    ): ScanResult {
        check(`interface` == currentResult.`interface`) { "Interfaces do not match: $`interface` != ${currentResult.`interface`}" }
        check(cidr == currentResult.cidr) { "Networks do not match: $cidr != ${currentResult.cidr}" }
        return ScanResult(
            `interface` = `interface`,
            cidr = cidr,
            hosts = buildSet {
                hosts.forEach { add(it.ip) }
                currentResult.hosts.forEach { add(it.ip) }
            }
                .sorted()
                .map { ip ->
                    val recordedHost = hosts.find { it.ip == ip }
                    val scannedHost = currentResult.hosts.find { it.ip == ip }
                    val mergedStatus = if (scannedHost != null) scannedHost.status else Status.DOWN
                    val mergedHost = Host(
                        ip = ip,
                        name = if (scannedHost != null) scannedHost.name else recordedHost?.name,
                        status = mergedStatus,
                        since = if (mergedStatus != recordedHost?.status) currentResult.timestamp else recordedHost?.since,
                        model = if (scannedHost != null) scannedHost.model else recordedHost?.model,
                        vendor = if (scannedHost != null) scannedHost.vendor else recordedHost?.vendor,
                        services = scannedHost?.services ?: recordedHost?.services,
                    )
                    if (mergedHost != recordedHost) onChange(mergedHost)
                    mergedHost
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

    companion object {
        private val logger by SLF4J

        fun load(
            file: Path,
            format: StringFormat = JsonFormat,
        ): ScanResult? = if (file.exists()) {
            file.readText().runCatching {
                format.decodeFromString<ScanResult>(this)
            }.fold(
                onSuccess = {
                    logger.info("Loaded stored scan from {}", v("file", file))
                    it
                },
                onFailure = { error ->
                    logger.error("Error loading scan result", error)
                    null
                },
            )
        } else {
            null
        }
    }
}
