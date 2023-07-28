package com.bkahlert.netmon

import com.bkahlert.kommons.logging.SLF4J
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val logger by SLF4J

fun ScanResult.Companion.load(
    file: Path = Paths.get(".netmon.scan.json"),
    format: StringFormat = JsonFormat,
): ScanResult? = file.takeIf { it.exists() }?.runCatching {
    format.decodeFromString<ScanResult>(readText())
}?.getOrElse { error ->
    logger.error("Error loading scan result", error)
    null
}

fun ScanResult.save(
    file: Path = Paths.get(".netmon.scan.json"),
    format: StringFormat = JsonFormat,
) = kotlin.runCatching {
    file.writeText(format.encodeToString(this))
}.getOrElse { error ->
    logger.error("Error saving scan result", error)
}
