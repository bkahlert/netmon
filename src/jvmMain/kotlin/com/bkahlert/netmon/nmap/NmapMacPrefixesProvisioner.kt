package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.age
import com.bkahlert.netmon.logging.SLF4J
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.days

class NmapMacPrefixesProvisioner(
    cache: FileCache,
) {

    private val logger by SLF4J

    private val data = cache.update(
        name = NMAP_MAC_PREFIXES_FILENAME,
        source = { url.openStream() },
    ) { it.age > 30.days }.inputStream()

    fun provision(): Path =
        createTempFile(prefix = NMAP_MAC_PREFIXES_FILENAME)
            .apply { outputStream().buffered().use { data.copyTo(it) } }
            .also { logger.info("Provisioned file={} at path={}", NMAP_MAC_PREFIXES_FILENAME, it) }

    fun provisionIn(directory: Path): Path =
        directory.createDirectories().resolve(NMAP_MAC_PREFIXES_FILENAME)
            .apply { outputStream().buffered().use { data.copyTo(it) } }
            .also { logger.info("Provisioned file={} at path={}", NMAP_MAC_PREFIXES_FILENAME, it) }

    companion object {
        public const val NMAP_MAC_PREFIXES_FILENAME = "nmap-mac-prefixes"
        private val url = URL("https://svn.nmap.org/nmap/$NMAP_MAC_PREFIXES_FILENAME")
    }
}
