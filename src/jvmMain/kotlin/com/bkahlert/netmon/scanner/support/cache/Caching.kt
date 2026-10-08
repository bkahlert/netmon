package com.bkahlert.netmon.scanner.support.cache

import com.bkahlert.netmon.scanner.support.logging.SLF4J
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Well-known directories of the running system. */
object SystemLocations {

    /** The working directory. */
    val Work: Path by lazy { Paths.get("").toAbsolutePath() }

    /** The home directory of the current user. */
    val Home: Path by lazy { Paths.get(System.getProperty("user.home")) }

    /** The directory for temporary files. */
    val Temp: Path by lazy { Paths.get(System.getProperty("java.io.tmpdir")) }

    /** The cache directory of the current user. */
    val Cache: Path by lazy {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        if (osName.indexOf("mac") >= 0) {
            (Home.takeIf { it.exists() } ?: Paths.get("/")) / "Library" / "Caches"
        } else if (osName.indexOf("nix") >= 0 || osName.indexOf("nux") >= 0 || osName.indexOf("aix") > 0) {
            when (val cacheHome = System.getenv("XDG_CACHE_HOME")) {
                null -> Home.takeIf { it.exists() }?.resolve(".cache") ?: Paths.get("/var/cache")
                else -> Paths.get(cacheHome)
            }
        } else {
            logger.warn("Unable to determine cache directory for OS: $osName")
            Temp
        }
    }
}

private val logger by SLF4J

/** The time elapsed since this file was last modified. */
val Path.age: Duration get() = (System.currentTimeMillis() - getLastModifiedTime().toMillis()).milliseconds

/** A simple [directory]-backed file cache. */
class FileCache(val directory: Path) {

    private val logger by SLF4J

    private val String.path: Path get() = directory.resolve(md5Checksum())

    private fun String.md5Checksum(): String =
        MessageDigest.getInstance("MD5").digest(toByteArray()).joinToString("") { "%02x".format(it) }

    private fun InputStream.copyTo(path: Path): Path = path.also {
        it.createParentDirectories()
        if (it.exists()) it.deleteRecursively()
        it.outputStream().buffered().use { out -> copyTo(out) }
    }

    private fun Path.copyTo(path: Path): Path = if (isRegularFile()) {
        inputStream().copyTo(path)
    } else if (isDirectory()) {
        path.also {
            it.createParentDirectories()
            if (it.exists()) it.deleteRecursively()
            it.createDirectory()
            listDirectoryEntries().forEach { entry -> entry.copyToRecursively(it.resolve(entry.fileName), followLinks = true) }
        }
    } else {
        throw IllegalArgumentException("Path $this is neither a file nor a directory.")
    }

    /** Returns the [Path] to the file with the given [name] or `null` if it does not exist. */
    fun get(name: String): Path? = name.path.takeIf { it.exists() }.also {
        if (it == null) logger.debug("Cache miss for name={}", name)
        else logger.debug("Cache hit for name={}", name)
    }

    /**
     * Stores the contents of the given [source] under the given [name], and returns the [Path] to the file.
     *
     * If a file with the given [name] already exists, it is overwritten.
     */
    fun put(name: String, source: InputStream): Path = source.copyTo(name.path).also {
        logger.debug("Updated data for name={}, reason=unconditional", name)
    }

    /**
     * Stores the contents of the given [source] under the given [name], and returns the [Path] to the file.
     *
     * If a file with the given [name] already exists, it is overwritten.
     */
    fun put(name: String, source: Path): Path = source.copyTo(name.path).also {
        logger.debug("Updated data for name={}, reason=unconditional", name)
    }

    /** Stores the contents of the given [source], if no file with the given [name] exists. */
    fun getOrPut(name: String, source: () -> InputStream): Path = get(name) ?: put(name, source())

    /** Stores the contents of the given [source], if no file with the given [name] exists. */
    fun getOrCollect(name: String, source: Path.() -> Unit): Path = get(name) ?: createTempDirectory(name).let { tempDir ->
        val result = kotlin.runCatching {
            source(tempDir)
            put(name, tempDir)
        }
        tempDir.deleteRecursively()
        result.getOrThrow()
    }

    /**
     * Stores the contents of the given [source],
     * if no file with the given [name] exists, yet, or
     * if the given [predicate] returns `true` for the existing file.
     *
     * If refreshing fails, returns the existing file when one is available; otherwise, propagates the failure.
     */
    fun update(name: String, source: () -> InputStream, predicate: (Path) -> Boolean): Path {
        val destination = name.path
        val cachedFile = destination.takeIf { it.exists() }
        if (cachedFile == null || predicate(cachedFile)) {
            var temporaryFile: Path? = null
            try {
                val downloadFile = Files.createTempFile(directory.createDirectories(), "cache-", ".tmp")
                temporaryFile = downloadFile
                source().use { it.copyTo(downloadFile) }
                Files.move(downloadFile, destination, ATOMIC_MOVE, REPLACE_EXISTING)
                logger.info(
                    "Updated data for name={}, reason={}",
                    name,
                    if (cachedFile == null) "cache-miss" else "predicate-match",
                )
            } catch (e: Exception) {
                if (cachedFile == null) throw e
                logger.warn("Failed to update data for name={}, using cached data", name, e)
            } finally {
                temporaryFile?.deleteIfExists()
            }
        }
        return destination
    }

    fun remove(name: String): Boolean = name.path.takeIf { it.exists() }?.let {
        it.deleteRecursively()
        logger.info("Removed data for name={}, reason=request", name)
        true
    } ?: false

    fun purge() {
        if (directory.exists()) {
            directory.listDirectoryEntries().forEach { it.deleteRecursively() }
            directory.deleteExisting()
        }
        logger.info("Purged all data, reason=request")
    }

    override fun toString(): String {
        val size = directory.fileSize()
        val (directories, files) = directory.listDirectoryEntries().partition { it.isDirectory() }
        return "${FileCache::class.simpleName}(location=file://$directory, size=$size B, directoryCount=${directories.size}, fileCount=${files.size})"
    }

    companion object {
        fun of(identifier: String): FileCache {
            require(identifier.isNotBlank()) { "Identifier must not be blank." }
            require(identifier.none { it == '/' || it == ':' }) { "Identifier must not contain '/' or ':'" }
            val directory = SystemLocations.Cache.resolve(identifier).createDirectories()
            return FileCache(directory)
        }
    }
}
