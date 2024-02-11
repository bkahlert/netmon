package com.bkahlert.kommons

import com.bkahlert.kommons.io.useBufferedOutputStream
import com.bkahlert.kommons.logging.SLF4J
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.kommons.logging.logback.StructuredArguments.kv
import com.bkahlert.netmon.logging.get
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Cache directory of the current user.
 */
@Suppress("RedundantVisibilityModifier")
public val SystemLocations.Cache: Path by lazy { _Cache }

private val logger = Logback.get(SystemLocations::class)
private val _Cache: Path
    get() {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        return if (osName.indexOf("mac") >= 0) {
            (SystemLocations.Home.takeIf { it.exists() } ?: Paths.get("/")) / "Library" / "Caches"
        } else if (osName.indexOf("nix") >= 0 || osName.indexOf("nux") >= 0 || osName.indexOf("aix") > 0) {
            when (val cacheHome = System.getenv("XDG_CACHE_HOME")) {
                null -> SystemLocations.Home.takeIf { it.exists() }?.resolve(".cache") ?: Paths.get("/var/cache")
                else -> Paths.get(cacheHome)
            }
        } else {
            logger.warn("Unable to determine cache directory for OS: $osName")
            SystemLocations.Temp
        }
    }

/** A simple [directory]-backed file cache. */
class FileCache(val directory: Path) {

    private val logger by SLF4J

    private val String.path: Path get() = directory.resolve(md5Checksum())

    private fun InputStream.copyTo(path: Path): Path = path.also {
        it.createParentDirectories()
        if (it.exists()) it.deleteRecursively()
        it.useBufferedOutputStream { out -> copyTo(out) }
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
        if (it == null) logger.debug("Cache miss for {}", kv("name", name))
        else logger.debug("Cache hit for {}", kv("name", name))
    }

    /**
     * Stores the contents of the given [source] under the given [name], and returns the [Path] to the file.
     *
     * If a file with the given [name] already exists, it is overwritten.
     */
    fun put(name: String, source: InputStream): Path = source.copyTo(name.path).also {
        logger.debug("Updated data for {}, {}", kv("name", name), kv("reason", "unconditional"))
    }

    /**
     * Stores the contents of the given [source] under the given [name], and returns the [Path] to the file.
     *
     * If a file with the given [name] already exists, it is overwritten.
     */
    fun put(name: String, source: Path): Path = source.copyTo(name.path).also {
        logger.debug("Updated data for {}, {}", kv("name", name), kv("reason", "unconditional"))
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
     */
    fun update(name: String, source: () -> InputStream, predicate: (Path) -> Boolean): Path = name.path.also {
        if (!it.exists()) {
            source().copyTo(it).also {
                logger.info("Updated data for {}, {}", kv("name", name), kv("reason", "cache-miss"))
            }
        } else if (predicate(it)) {
            source().copyTo(it).also {
                logger.info("Updated data for {}, {}", kv("name", name), kv("reason", "predicate-match"))
            }
        }
    }

    fun remove(name: String): Boolean = name.path.takeIf { it.exists() }?.let {
        it.deleteRecursively()
        logger.info("Removed data for {}, {}", kv("name", name), kv("reason", "request"))
        true
    } ?: false

    fun purge() {
        if (directory.exists()) {
            directory.listDirectoryEntries().forEach { it.deleteRecursively() }
            directory.deleteExisting()
        }
        logger.info("Purged all data, {}", kv("reason", "request"))
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
