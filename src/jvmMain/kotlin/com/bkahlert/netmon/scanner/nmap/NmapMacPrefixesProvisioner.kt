package com.bkahlert.netmon.scanner.nmap

import com.bkahlert.netmon.scanner.support.cache.FileCache
import com.bkahlert.netmon.scanner.support.cache.age
import com.bkahlert.netmon.scanner.support.logging.SLF4J
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.days

class NmapMacPrefixesProvisioner(
    cache: FileCache,
) {

    private val logger by SLF4J

    private val data = run {
        cache.get(NMAP_MAC_PREFIXES_FILENAME)
            ?.takeIf { it.fileSize() == 0L }
            ?.let { cache.remove(NMAP_MAC_PREFIXES_FILENAME) }
        cache.update(
            name = NMAP_MAC_PREFIXES_FILENAME,
            source = { url.openNonEmptyStream() },
        ) { it.age > 30.days }.inputStream()
    }

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

        private fun URL.openNonEmptyStream(): InputStream {
            val input = PushbackInputStream(openStream(), 1)
            try {
                val firstByte = input.read()
                if (firstByte == -1) throw IOException("Downloaded MAC prefix table is empty")
                input.unread(firstByte)
                return input
            } catch (e: IOException) {
                try {
                    input.close()
                } catch (closeError: IOException) {
                    e.addSuppressed(closeError)
                }
                throw e
            }
        }
    }
}
