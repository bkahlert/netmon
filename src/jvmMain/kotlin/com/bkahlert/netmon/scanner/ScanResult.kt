package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.logging.SLF4J
import net.logstash.logback.argument.StructuredArguments.v
import com.bkahlert.netmon.serialization.InstantAsEpochSecondsSerializer
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
import java.nio.channels.ClosedChannelException
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
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
                    val scannedHost = currentResult.hosts.find { it.ip == ip } // TODO improve detection, e.g. by MAC address and/or hostname
                    val mergedStatus: Status = if (scannedHost != null) scannedHost.status ?: Status.UP else Status.DOWN
                    val mergedHost = Host(
                        ip = ip,
                        name = if (mergedStatus == Status.UP) scannedHost?.name else recordedHost?.name,
                        status = mergedStatus,
                        since = if (mergedStatus != recordedHost?.status) currentResult.timestamp else recordedHost.since,
                        model = if (mergedStatus == Status.UP) scannedHost?.model else recordedHost?.model,
                        vendor = if (mergedStatus == Status.UP) scannedHost?.vendor else recordedHost?.vendor,
                        services = if (mergedStatus == Status.UP) scannedHost?.services else recordedHost?.services,
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
    ) = try {
        val content = format.encodeToString(this)
        val tempFile = createTempFile(file.name, ".tmp")
        tempFile.writeText(content)
        tempFile.moveTo(file, overwrite = true)
    } catch (e: ClosedChannelException) {
        logger.info("Aborted saving scan result to {} was aborted", v("file", file.toAbsolutePath()))
    } catch (e: Throwable) {
        logger.error("Error saving scan result to {}", v("file", file.toAbsolutePath()), e)
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
