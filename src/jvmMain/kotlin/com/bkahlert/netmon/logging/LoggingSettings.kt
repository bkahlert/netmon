package com.bkahlert.netmon.logging

import com.bkahlert.kommons.config.Settings
import com.bkahlert.kommons.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.serialization.JsonFormat

object LoggingSettings : Settings() {
    val verbosity: Verbosity by setting(default = Verbosity.ERRORS_AND_WARNINGS, name = "verbosity")
    val debug: Debug by setting(default = Debug(), stringFormat = JsonFormat.unquoted, name = "debug")
    fun apply(vararg args: String) {
        (Verbosity.from(*args).takeIf { it.ordinal > 0 } ?: verbosity)
            .levels
            .let { debug.apply(it) }
            .let { SimpleLogger.configure(it) }
    }
}
