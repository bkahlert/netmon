package com.bkahlert.netmon.scanner.nmap

import com.bkahlert.netmon.scanner.support.cache.SystemLocations
import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.contract.serialization.JsonFormat
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isWritable

/** Settings for the [NmapNetworkScanner]. */
object NmapSettings : Settings("nmap", JsonFormat.unquoted) {

    /** Whether the [NmapNetworkScanner] should scan privileged. */
    val privileged: Boolean by setting(default = true)

    /** Directory the [NmapNetworkScanner] looks for [customized data files](https://nmap.org/book/data-files-replacing-data-files.html). */
    val dataDir: Path?
        get() = _dataDir?.let {
            Paths.get(it).also { dir ->
                if (dir.exists()) require(dir.isWritable()) { "Data directory $dir is not writeable" }
                else require(dir.parent?.isWritable() == true) { "Data directory $dir does not exist and ${dir.parent} is not writeable" }
            }
        }
    private val _dataDir: String? by setting(default = SystemLocations.NetmonCache.resolve("nmap").toString(), name = "dataDir")
}
