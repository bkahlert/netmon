package com.bkahlert.netmon.nmap

import com.bkahlert.kommons.config.Settings
import com.bkahlert.kommons.config.setting
import java.nio.file.Path
import java.nio.file.Paths

/** Settings for the [NmapNetworkScanner]. */
object NmapSettings : Settings("nmap") {

    /** Whether the [NmapNetworkScanner] should scan privileged. */
    val privileged: Boolean by setting(default = true)

    /** Directory the [NmapNetworkScanner] looks for [customized data files](https://nmap.org/book/data-files-replacing-data-files.html). */
    val dataDir: Path? get() = _dataDir?.let { Paths.get(it) }
    private val _dataDir: String? by setting(default = "./nmap", name = "dataDir")
}
