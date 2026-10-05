package com.bkahlert.netmon.ssdp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A search response or `NOTIFY` as a device sent it; header names are uppercase. */
data class SsdpMessage(val startLine: String, val headers: Map<String, String>) {

    val location: String? get() = headers["LOCATION"]
    val server: String? get() = headers["SERVER"]
    val notificationType: String? get() = headers["ST"] ?: headers["NT"]
    val usn: String? get() = headers["USN"]
    val isByebye: Boolean get() = headers["NTS"].equals("ssdp:byebye", ignoreCase = true)
    val maxAge: Duration? get() = headers["CACHE-CONTROL"]?.let { MAX_AGE.find(it)?.groups?.get("seconds")?.value?.toIntOrNull()?.seconds }

    companion object {
        private val MAX_AGE = Regex("max-age\\s*=\\s*(?<seconds>\\d+)", RegexOption.IGNORE_CASE)

        /** Returns the message, or `null` for a search request or text that is not an SSDP message. */
        fun parse(text: String): SsdpMessage? {
            val lines = text.split("\r\n", "\n")
            val startLine = lines.firstOrNull()?.trim() ?: return null
            if (!(startLine.startsWith("HTTP/1.1 200") || startLine.startsWith("NOTIFY "))) return null
            val headers = lines.drop(1).takeWhile { it.isNotBlank() }.mapNotNull { line ->
                val colon = line.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
                line.substring(0, colon).trim().uppercase() to line.substring(colon + 1).trim()
            }.toMap()
            return SsdpMessage(startLine, headers)
        }
    }
}
