package com.bkahlert.netmon.scanner.support.logging

import com.bkahlert.netmon.support.config.Settings
import com.bkahlert.netmon.support.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.contract.serialization.JsonFormat

object LoggingSettings : Settings(defaultFormat = JsonFormat.unquoted) {
    val verbosity: Verbosity by setting(default = Verbosity.ERRORS_AND_WARNINGS, name = "verbosity")
    val debug: Debug by setting(default = Debug(), name = "debug")
    fun apply(vararg args: String) {
        (Verbosity.from(*args).takeIf { it.ordinal > 0 } ?: verbosity)
            .levels
            .let { debug.apply(it) }
            .let { SimpleLogger.configure(it) }
    }
}
