package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.logging.SLF4J
import com.bkahlert.netmon.serialization.InstantAsEpochSecondsSerializer
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Status
import com.bkahlert.netmon.serialization.JsonFormat
import kotlin.time.Duration
import kotlin.time.Instant
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
        downAfter: Duration,
        notBefore: Instant,
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
                    val mergedHost = mergeHost(recordedHost, scannedHost, currentResult.timestamp, downAfter, notBefore)
                    if (recordedHost == null || recordedHost.status != mergedHost.status) onChange(mergedHost)
                    mergedHost
                },
            timestamp = currentResult.timestamp,
        )
    }

    private fun mergeHost(recorded: Host?, scanned: Host?, scanTime: Instant, downAfter: Duration, notBefore: Instant): Host = when {
        scanned != null && (scanned.status == null || scanned.status == Status.UP) -> scanned.copy(
            name = scanned.name ?: recorded?.name,
            status = Status.UP,
            since = if (recorded != null && recorded.status == Status.UP) recorded.since ?: scanTime else scanTime,
            lastSeen = scanTime,
            model = scanned.model ?: recorded?.model,
            vendor = scanned.vendor ?: recorded?.vendor,
            services = scanned.services ?: recorded?.services,
        )

        recorded == null -> checkNotNull(scanned).copy(status = Status.DOWN, since = scanTime, lastSeen = null)

        recorded.status == Status.UP -> {
            val lastSeen = recorded.lastSeen ?: timestamp
            if (scanTime - maxOf(lastSeen, notBefore) < downAfter) recorded.copy(lastSeen = lastSeen)
            else recorded.copy(status = Status.DOWN, since = lastSeen, lastSeen = lastSeen)
        }

        recorded.status == Status.DOWN -> recorded

        else -> recorded.copy(status = Status.DOWN, since = scanTime)
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
        logger.info("Aborted saving scan result to {} was aborted", file.toAbsolutePath())
    } catch (e: Throwable) {
        logger.error("Error saving scan result to {}", file.toAbsolutePath(), e)
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
                    logger.info("Loaded stored scan from {}", file)
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
