package com.bkahlert.netmon.scanner.state

import com.bkahlert.netmon.scanner.scan.ScanResult
import com.bkahlert.netmon.scanner.support.logging.SLF4J
import com.bkahlert.netmon.contract.serialization.JsonFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.channels.ClosedChannelException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createTempFile
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

class JsonScanStateStore(
    private val file: Path,
    private val format: StringFormat = JsonFormat,
) : ScanStateStore {

    private val logger by SLF4J

    override fun load(): ScanResult? = if (file.exists()) {
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

    override fun save(scan: ScanResult) {
        try {
            val content = format.encodeToString(scan)
            val tempFile = createTempFile(file.toAbsolutePath().parent, file.name, ".tmp")
            tempFile.writeText(content)
            tempFile.moveTo(file, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: ClosedChannelException) {
            logger.info("Aborted saving scan result to {} was aborted", file.toAbsolutePath())
        } catch (e: Throwable) {
            logger.error("Error saving scan result to {}", file.toAbsolutePath(), e)
        }
    }
}
