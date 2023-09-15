package com.bkahlert.netmon.logging

import com.bkahlert.kommons.config.Settings
import com.bkahlert.kommons.config.setting
import com.bkahlert.kommons.logging.logback.Logback
import com.bkahlert.kommons.serialization.UnquotedStringsFormat.Companion.unquoted
import com.bkahlert.netmon.serialization.JsonFormat

object LoggingSettings : Settings() {
    val verbosity: Verbosity by setting(default = Verbosity.ERRORS_ONLY, name = "verbosity")
    val debug: Debug by setting(default = Debug(), stringFormat = JsonFormat.unquoted, name = "debug")
    fun apply(vararg args: String) {
        (Verbosity.from(*args).takeIf { it.ordinal > 0 } ?: verbosity)
            .levels
            .let { debug.apply(it) }
            .let { Logback.levels(it) }
    }
}
