package com.bkahlert.netmon

import com.bkahlert.io.Logger
import com.bkahlert.serialization.JsonFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun ScanResult.Companion.load(
    file: Path = Paths.get(".netmon.scan.json"),
    format: StringFormat = JsonFormat,
): ScanResult? = file.takeIf { it.exists() }?.runCatching {
    format.decodeFromString<ScanResult>(readText())
}?.getOrElse { error ->
    Logger.error("Error loading scan result: $error")
    null
}

fun ScanResult.save(
    file: Path = Paths.get(".netmon.scan.json"),
    format: StringFormat = JsonFormat,
) = kotlin.runCatching {
    file.writeText(format.encodeToString(this))
}.getOrElse {
    Logger.error("Error saving scan result: $it")
}
