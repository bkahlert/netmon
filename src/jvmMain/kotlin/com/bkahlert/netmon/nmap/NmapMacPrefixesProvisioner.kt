package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.FileCache
import com.bkahlert.kommons.io.age
import com.bkahlert.kommons.io.useBufferedOutputStream
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.days

class NmapMacPrefixesProvisioner(
    private val cache: FileCache,
) {

    private val logger by SLF4J

    private val data = cache.update(
        name = NMAP_MAC_PREFIXES_FILENAME,
        source = { url.openStream() },
    ) { it.age > 30.days }.inputStream()

    fun provision(): Path =
        createTempFile(prefix = NMAP_MAC_PREFIXES_FILENAME)
            .useBufferedOutputStream { data.copyTo(it) }
            .also { logger.info("Provisioned {} at {}", kv("file", NMAP_MAC_PREFIXES_FILENAME), kv("path", it)) }

    fun provisionIn(directory: Path): Path =
        directory.createDirectories().resolve(NMAP_MAC_PREFIXES_FILENAME)
            .useBufferedOutputStream { data.copyTo(it) }
            .also { logger.info("Provisioned {} at {}", kv("file", NMAP_MAC_PREFIXES_FILENAME), kv("path", it)) }

    companion object {
        public const val NMAP_MAC_PREFIXES_FILENAME = "nmap-mac-prefixes"
        private val url = URL("https://svn.nmap.org/nmap/$NMAP_MAC_PREFIXES_FILENAME")
    }
}
