package com.bkahlert.netmon.scanner

import com.bkahlert.netmon.serialization.InstantAsEpochSecondsSerializer
import com.bkahlert.netmon.Cidr
import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.Status
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    ): MergeResult {
        check(`interface` == currentResult.`interface`) { "Interfaces do not match: $`interface` != ${currentResult.`interface`}" }
        check(cidr == currentResult.cidr) { "Networks do not match: $cidr != ${currentResult.cidr}" }
        val changedHosts = mutableListOf<Host>()
        val scan = ScanResult(
            `interface` = `interface`,
            cidr = cidr,
            hosts = pair(hosts, currentResult.hosts)
                .map { (recordedHost, scannedHost) ->
                    val mergedHost = mergeHost(recordedHost, scannedHost, currentResult.timestamp, downAfter, notBefore)
                    if (recordedHost == null || recordedHost.status != mergedHost.status) changedHosts += mergedHost
                    mergedHost
                }
                .sortedBy { it.ip },
            timestamp = currentResult.timestamp,
        )
        return MergeResult(scan = scan, changedHosts = changedHosts)
    }

    private fun mergeHost(recorded: Host?, scanned: Host?, scanTime: Instant, downAfter: Duration, notBefore: Instant): Host = when {
        scanned != null && scanned.seenUp -> scanned.copy(
            name = scanned.name ?: recorded?.name,
            status = Status.UP,
            since = if (recorded != null && recorded.status == Status.UP) recorded.since ?: scanTime else scanTime,
            lastSeen = scanTime,
            model = scanned.model ?: recorded?.model,
            vendor = scanned.vendor ?: recorded?.vendor,
            services = scanned.services ?: recorded?.services,
            mac = scanned.mac ?: recorded?.mac,
            kind = scanned.kind?.takeUnless { it == Kind.GENERIC } ?: recorded?.kind ?: scanned.kind,
            link = scanned.link ?: recorded?.link,
            speed = scanned.speed ?: recorded?.speed,
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

    /**
     * Pairs each scanned host with the recorded host it is, and each recorded host the scan did not match with `null`.
     *
     * A recorded host that no scanned host matched is left out when a scanned host that is up holds its IP:
     * another device took it over, and the list must not hold two hosts with one IP.
     */
    private fun pair(recorded: List<Host>, scanned: List<Host>): List<Pair<Host?, Host?>> {
        val unmatchedRecorded = recorded.toMutableList()
        val pairs = mutableListOf<Pair<Host?, Host?>>()
        val unmatchedScanned = mutableListOf<Host>()

        scanned.forEach { host ->
            val index = host.mac?.let { mac -> unmatchedRecorded.indexOfFirst { it.mac == mac } } ?: -1
            if (index >= 0) pairs += unmatchedRecorded.removeAt(index) to host else unmatchedScanned += host
        }
        unmatchedScanned.forEach { host ->
            val index = unmatchedRecorded.indexOfFirst { it.ip == host.ip && (it.mac == null || host.mac == null) }
            pairs += (if (index >= 0) unmatchedRecorded.removeAt(index) else null) to host
        }

        val takenIps = scanned.filter { it.seenUp }.map { it.ip }.toSet()
        unmatchedRecorded.filter { it.ip !in takenIps }.forEach { pairs += it to null }
        return pairs
    }

    private val Host.seenUp: Boolean get() = status == null || status == Status.UP
}
